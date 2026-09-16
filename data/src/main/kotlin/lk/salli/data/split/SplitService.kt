package lk.salli.data.split

import androidx.room.withTransaction
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.SplitExpenseEntity
import lk.salli.data.db.entities.SplitGroupEntity
import lk.salli.data.db.entities.SplitMemberEntity
import lk.salli.data.db.entities.SplitSettlementEntity
import lk.salli.data.db.entities.SplitShareEntity
import lk.salli.domain.split.SplitError
import lk.salli.domain.split.SplitMath
import lk.salli.domain.split.SplitMethod
import lk.salli.domain.split.SplitParticipant
import lk.salli.domain.split.SplitResult

/**
 * The shared-expense ledger: groups, members, expenses with their shares, and settlements.
 * It does not change Home, Insights or Budgets totals in this phase; it is bookkeeping between people.
 */
class SplitService(
    private val db: SalliDatabase,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    sealed interface Outcome {
        data class Saved(val id: Long) : Outcome
        data class Rejected(val error: SplitError) : Outcome
    }

    /** What the transaction detail sheet shows for a transaction that was split. */
    data class LinkedSplit(val groupId: Long, val groupName: String, val myShareMinor: Long?, val currency: String)

    /** Creates a group with the user already in it as its "me" member. */
    suspend fun createGroup(name: String, currency: String, meName: String): Long = db.withTransaction {
        val groupId = db.split().insertGroup(
            SplitGroupEntity(name = name.trim(), currency = currency.uppercase(), createdAt = now()),
        )
        db.split().insertMember(SplitMemberEntity(groupId = groupId, name = meName.trim().ifBlank { "Me" }, isMe = true))
        groupId
    }

    suspend fun addMember(groupId: Long, name: String): Outcome {
        if (db.split().groupById(groupId) == null) return Outcome.Rejected(SplitError.UNKNOWN_GROUP)
        return Outcome.Saved(db.split().insertMember(SplitMemberEntity(groupId = groupId, name = name.trim())))
    }

    /** Validates, splits and stores an expense and its shares in one transaction. */
    suspend fun addExpense(
        groupId: Long,
        title: String,
        amountMinor: Long,
        currency: String,
        paidByMemberId: Long,
        method: SplitMethod,
        participants: List<SplitParticipant>,
        at: Long = now(),
        linkedTransactionId: Long? = null,
        note: String? = null,
    ): Outcome = db.withTransaction {
        val group = db.split().groupById(groupId)
            ?: return@withTransaction Outcome.Rejected(SplitError.UNKNOWN_GROUP)
        SplitMath.currencyError(group.currency, currency)?.let { return@withTransaction Outcome.Rejected(it) }
        val memberIds = db.split().membersOf(groupId).mapTo(HashSet()) { it.id }
        if (paidByMemberId !in memberIds || participants.any { it.memberId !in memberIds }) {
            return@withTransaction Outcome.Rejected(SplitError.UNKNOWN_MEMBER)
        }
        when (val result = SplitMath.shares(amountMinor, participants, method)) {
            is SplitResult.Invalid -> Outcome.Rejected(result.error)
            is SplitResult.Ok -> {
                val expenseId = db.split().insertExpense(
                    SplitExpenseEntity(
                        groupId = groupId,
                        title = title.trim(),
                        amountMinor = amountMinor,
                        paidByMemberId = paidByMemberId,
                        method = method.name,
                        at = at,
                        linkedTransactionId = linkedTransactionId,
                        note = note?.trim()?.ifBlank { null },
                    ),
                )
                db.split().insertShares(
                    result.shares.filterValues { it > 0L }
                        .map { (member, share) -> SplitShareEntity(expenseId = expenseId, memberId = member, shareMinor = share) },
                )
                Outcome.Saved(expenseId)
            }
        }
    }

    /** Records [fromMemberId] paying [toMemberId]; optionally tied to a real bank transaction. */
    suspend fun recordSettlement(
        groupId: Long,
        fromMemberId: Long,
        toMemberId: Long,
        amountMinor: Long,
        at: Long = now(),
        linkedTransactionId: Long? = null,
    ): Outcome = db.withTransaction {
        val group = db.split().groupById(groupId)
            ?: return@withTransaction Outcome.Rejected(SplitError.UNKNOWN_GROUP)
        if (fromMemberId == toMemberId) return@withTransaction Outcome.Rejected(SplitError.SAME_MEMBER)
        if (amountMinor <= 0L) return@withTransaction Outcome.Rejected(SplitError.NON_POSITIVE_TOTAL)
        val memberIds = db.split().membersOf(groupId).mapTo(HashSet()) { it.id }
        if (fromMemberId !in memberIds || toMemberId !in memberIds) {
            return@withTransaction Outcome.Rejected(SplitError.UNKNOWN_MEMBER)
        }
        if (linkedTransactionId != null) {
            val tx = db.transactions().byId(linkedTransactionId)
            if (tx != null) {
                SplitMath.currencyError(group.currency, tx.amountCurrency)?.let { return@withTransaction Outcome.Rejected(it) }
            }
        }
        Outcome.Saved(
            db.split().insertSettlement(
                SplitSettlementEntity(
                    groupId = groupId,
                    fromMemberId = fromMemberId,
                    toMemberId = toMemberId,
                    amountMinor = amountMinor,
                    at = at,
                    linkedTransactionId = linkedTransactionId,
                ),
            ),
        )
    }

    suspend fun deleteExpense(expenseId: Long) = db.split().deleteExpense(expenseId)

    suspend fun deleteSettlement(settlementId: Long) = db.split().deleteSettlement(settlementId)

    suspend fun setArchived(groupId: Long, archived: Boolean) = db.split().setArchived(groupId, archived)

    suspend fun deleteGroup(groupId: Long) = db.split().deleteGroup(groupId)

    /** The newest split made from [transactionId], with the user's own share of it. */
    suspend fun linkedSplit(transactionId: Long): LinkedSplit? {
        val expense = db.split().expenseLinkedTo(transactionId) ?: return null
        val group = db.split().groupById(expense.groupId) ?: return null
        val me = db.split().membersOf(group.id).firstOrNull { it.isMe }
        val myShare = me?.let { m -> db.split().sharesOf(expense.id).firstOrNull { it.memberId == m.id }?.shareMinor ?: 0L }
        return LinkedSplit(groupId = group.id, groupName = group.name, myShareMinor = myShare, currency = group.currency)
    }
}
