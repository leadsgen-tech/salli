package lk.salli.data.planning

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.RecurringSeriesEntity
import lk.salli.domain.recurring.RecurringDetector
import lk.salli.domain.recurring.RecurringInput
import lk.salli.domain.recurring.RecurringStatus

/**
 * Feeds stored transactions to [RecurringDetector] and keeps `recurring_series` in step with it.
 *
 * Detection columns are rewritten on every pass; the user's confirm / not-recurring choice and
 * the reminder markers are carried over. A series that stops showing up is deleted if the user
 * never acted on it, and kept (flagged not detected) if they did, so a dismissal is never lost.
 */
class RecurringService(
    private val db: SalliDatabase,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    fun observe(): Flow<List<RecurringSeriesEntity>> = db.recurring().observeAll()

    suspend fun setUserState(id: Long, state: String) = db.recurring().setUserState(id, state, now())

    /** @return number of rows inserted, updated or deleted. */
    suspend fun recompute(): Int = db.withTransaction {
        val t = now()
        val hidden = db.accounts().all().filter { it.isHidden }.mapTo(HashSet()) { it.id }
        val categories = db.categories().all().associate { it.id to it.name }
        val inputs = db.transactions().recentAll(t - LOOKBACK_MS)
            .filter { !it.isHidden && it.accountId !in hidden }
            .map { tx ->
                RecurringInput(
                    id = tx.id,
                    counterparty = tx.note?.takeIf { it.isNotBlank() } ?: tx.merchantRaw,
                    flowId = tx.flowId,
                    currency = tx.amountCurrency,
                    amountMinor = tx.amountMinor,
                    timestamp = tx.timestamp,
                    isDeclined = tx.isDeclined,
                    isOwnTransfer = tx.transferGroupId != null,
                    categoryName = tx.categoryId?.let(categories::get),
                )
            }

        val existing = db.recurring().all().associateBy { Triple(it.seriesKey, it.flowId, it.currency) }
        val seen = HashSet<Triple<String, Int, String>>()
        var changed = 0
        for (s in RecurringDetector.detect(inputs, t)) {
            val key = Triple(s.key, s.flowId, s.currency)
            seen += key
            val old = existing[key]
            val fresh = RecurringSeriesEntity(
                id = old?.id ?: 0L,
                seriesKey = s.key,
                displayName = s.displayName,
                flowId = s.flowId,
                currency = s.currency,
                cadence = s.cadence?.name,
                intervalDays = s.intervalDays,
                typicalAmountMinor = s.typicalAmountMinor,
                isFixed = s.isFixed,
                occurrences = s.occurrences,
                declinedAttempts = s.declinedAttempts,
                lastAt = s.lastAt,
                nextAt = s.nextAt,
                status = s.status.name,
                confidence = s.confidence,
                userState = old?.userState ?: RecurringSeriesEntity.AUTO,
                isDetected = true,
                notifiedDueAt = old?.notifiedDueAt,
                // The "keeps failing" notice re-arms once the series recovers.
                failingNotified = s.status == RecurringStatus.FAILING && old?.failingNotified == true,
                updatedAt = old?.updatedAt ?: t,
            )
            when {
                old == null -> { db.recurring().insert(fresh.copy(updatedAt = t)); changed++ }
                old != fresh -> { db.recurring().update(fresh.copy(updatedAt = t)); changed++ }
            }
        }
        for ((key, old) in existing) {
            if (key in seen) continue
            if (old.userState == RecurringSeriesEntity.AUTO) {
                db.recurring().deleteById(old.id)
                changed++
            } else if (old.isDetected) {
                db.recurring().update(old.copy(isDetected = false, updatedAt = t))
                changed++
            }
        }
        changed
    }

    private companion object {
        /** Long enough for three yearly charges plus slack. */
        const val LOOKBACK_MS = 800L * 24 * 60 * 60 * 1000
    }
}
