package lk.salli.data.ingest

import androidx.room.withTransaction
import java.security.MessageDigest
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.BillEntity
import lk.salli.data.db.entities.FuelPassRecordEntity
import lk.salli.parser.utility.BillKind
import lk.salli.parser.utility.BillParser
import lk.salli.parser.utility.FuelPassParser
import lk.salli.parser.utility.ParsedBill
import lk.salli.parser.utility.UtilitySenders

/**
 * SMS → bills / fuel_pass_records. Sits beside [TransactionIngestor] and is handed every
 * message from a [UtilitySenders] sender.
 *
 * Bills are stored as the messages arrived and their status is *derived*: [replayAccount] walks
 * one account's messages in arrival order and decides from scratch what is open, part-paid, paid
 * or superseded. Ingest and the startup recompute run the same replay, so they cannot disagree,
 * and re-imported or out-of-order SMS converge on the same answer.
 *
 * Rules, applied in arrival order:
 *  - A statement (ISSUED) supersedes every earlier bill still open: billers like SLT roll any
 *    unpaid balance into the new "Total Payable". A statement at or below zero is in credit and
 *    counts as settled.
 *  - A payment receipt goes against the newest open bill. If it covers what is left (within
 *    [SETTLE_TOLERANCE_MINOR] of rounding) that bill and any older open ones close; otherwise the
 *    amount is recorded and the rest stays due.
 *  - A reminder refreshes a bill that only exists because of an earlier reminder. It never
 *    touches a statement, whose figures are authoritative.
 *  - A bill the user marked paid by hand stays paid.
 *
 * Re-imports are no-ops because every stored SMS body is hashed with a unique index.
 */
class UtilityIngestor(
    private val db: SalliDatabase,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun ingest(sender: String, body: String, receivedAt: Long): IngestResult {
        if (UtilitySenders.isFuelPassSender(sender)) {
            val fuel = FuelPassParser.parse(sender, body, receivedAt)
                ?: return IngestResult.Utility("fuel-pass: not a fill-up")
            val id = db.fuelPass().insert(
                FuelPassRecordEntity(
                    vehicle = fuel.vehicle,
                    litresMilli = fuel.litresMilli,
                    weeklyBalanceMilli = fuel.weeklyBalanceMilli,
                    stationCode = fuel.stationCode,
                    timestamp = fuel.timestampMillis,
                    resetsOn = fuel.resetsOnMillis,
                    rawBody = fuel.rawBody,
                    createdAt = now(),
                ),
            )
            return IngestResult.Utility(if (id > 0) "fuel-pass: stored" else "fuel-pass: duplicate")
        }

        val bill = BillParser.parse(sender, body) ?: return IngestResult.Utility("bill: not a bill")
        return db.withTransaction { persistBill(bill, receivedAt) }
    }

    private suspend fun persistBill(bill: ParsedBill, receivedAt: Long): IngestResult {
        val hash = sha256(bill.rawBody)
        if (db.bills().byHash(hash) != null) return IngestResult.Utility("bill: duplicate")
        val ts = now()

        val label = when (bill.kind) {
            BillKind.PAYMENT_RECEIVED -> {
                val openBefore = db.bills().openForBefore(bill.biller, bill.accountRef, before = receivedAt + 1)
                db.bills().insert(shadowRow(bill, hash, receivedAt, ts))
                replayAccount(bill.biller, bill.accountRef, ts)
                when {
                    openBefore.isEmpty() -> "bill: payment with no open bill"
                    db.bills().byId(openBefore.last().id)?.isPaid == true -> "bill: paid"
                    else -> "bill: partly paid"
                }
            }
            BillKind.REMINDER, BillKind.OVERDUE -> {
                val open = db.bills().newestOpenFor(bill.biller, bill.accountRef)
                when {
                    open != null -> {
                        // SLT sends "settle Rs X before <date>" reminders that quote only the
                        // *previous* month's leftover, so a reminder must never change a
                        // statement. Only a reminder-born row takes the new figures.
                        val statementOpen = BillKind.fromId(open.kindId) == BillKind.ISSUED
                        if (!statementOpen) {
                            val due = bill.dueDateMillis ?: open.dueDate
                            db.bills().update(
                                open.copy(
                                    amountDueMinor = bill.amountDueMinor,
                                    dueDate = due,
                                    periodLabel = open.periodLabel ?: bill.periodLabel,
                                    kindId = bill.kind.id,
                                    // A new due date is a new deadline: the "due soon" and "due
                                    // today" notices already sent were about the old one.
                                    reminderStage = if (due != open.dueDate) 0 else open.reminderStage,
                                    updatedAt = ts,
                                ),
                            )
                        }
                        db.bills().insert(shadowRow(bill, hash, receivedAt, ts))
                        if (statementOpen) "bill: reminder noted" else "bill: updated"
                    }
                    bill.amountDueMinor <= 0L -> {
                        db.bills().insert(shadowRow(bill, hash, receivedAt, ts))
                        "bill: nothing due"
                    }
                    else -> {
                        db.bills().insert(billRow(bill, hash, receivedAt, ts))
                        replayAccount(bill.biller, bill.accountRef, ts)
                        "bill: stored"
                    }
                }
            }
            BillKind.ISSUED -> {
                db.bills().insert(billRow(bill, hash, receivedAt, ts))
                replayAccount(bill.biller, bill.accountRef, ts)
                if (bill.amountDueMinor <= 0L) "bill: in credit" else "bill: stored"
            }
        }
        return IngestResult.Utility(label)
    }

    /**
     * Restores statement figures from their own SMS text, then replays every account. Idempotent;
     * safe to run on every start.
     *
     * @return number of rows whose stored state changed.
     */
    suspend fun recomputeBillStatuses(): Int = db.withTransaction {
        var changed = 0
        val ts = now()
        // An earlier build let a later reminder overwrite a statement's total and due date. The
        // stored body is the truth, so put the statement back before deriving statuses.
        for (row in db.bills().allByReceived()) {
            val parsed = BillParser.parseBody(row.rawBody) ?: continue
            if (parsed.kind != BillKind.ISSUED) continue
            val dueChanged = row.dueDate != parsed.dueDateMillis
            val drift = row.amountDueMinor != parsed.amountDueMinor || dueChanged ||
                BillKind.fromId(row.kindId) != BillKind.ISSUED
            if (drift) {
                db.bills().update(
                    row.copy(
                        amountDueMinor = parsed.amountDueMinor,
                        dueDate = parsed.dueDateMillis,
                        periodLabel = parsed.periodLabel ?: row.periodLabel,
                        kindId = BillKind.ISSUED.id,
                        reminderStage = if (dueChanged) 0 else row.reminderStage,
                        updatedAt = ts,
                    ),
                )
                changed++
            }
        }
        for (key in db.bills().billAccounts()) changed += replayAccount(key.biller, key.accountRef, ts)
        changed
    }

    private class Status(
        var paid: Boolean,
        var paidAt: Long?,
        var paidMinor: Long?,
        var superseded: Boolean,
        val manual: Boolean,
    )

    /** Derives open / part-paid / paid / superseded for one account. Returns rows changed. */
    private suspend fun replayAccount(biller: String, accountRef: String, ts: Long): Int {
        val rows = db.bills().forAccount(biller, accountRef)
        val receiptTimes = rows
            .filter { BillKind.fromId(it.kindId) == BillKind.PAYMENT_RECEIVED }
            .mapTo(HashSet()) { it.receivedAt }
        val bills = ArrayList<BillEntity>()
        val status = HashMap<Long, Status>()

        fun openAt(at: Long): List<BillEntity> = bills.filter { b ->
            val s = status.getValue(b.id)
            !s.paid && !s.superseded && b.receivedAt <= at
        }

        for (row in rows) {
            val kind = BillKind.fromId(row.kindId)
            if (kind == BillKind.PAYMENT_RECEIVED) {
                val amount = row.paidAmountMinor ?: 0L
                val open = openAt(row.receivedAt)
                if (amount <= 0L || open.isEmpty()) continue
                val newest = status.getValue(open.last().id)
                val already = newest.paidMinor ?: 0L
                val remaining = open.last().amountDueMinor - already
                newest.paidMinor = already + amount
                if (amount >= remaining - SETTLE_TOLERANCE_MINOR) {
                    newest.paid = true
                    newest.paidAt = row.receivedAt
                    // The newest bill already includes anything older still open.
                    open.dropLast(1).forEach { older ->
                        status.getValue(older.id).apply { paid = true; paidAt = row.receivedAt }
                    }
                }
                continue
            }
            if (isShadow(row)) continue

            if (kind == BillKind.ISSUED) openAt(row.receivedAt).forEach { status.getValue(it.id).superseded = true }

            // Rows paid by an older build's "Mark paid" have no flag yet. They are recognisable:
            // paid, with a paid time that matches no receipt for the account.
            val manual = row.paidManually ||
                (row.isPaid && row.amountDueMinor > 0L && row.paidAt != null && row.paidAt !in receiptTimes)
            status[row.id] = when {
                manual -> Status(true, row.paidAt, row.paidAmountMinor, superseded = false, manual = true)
                kind == BillKind.ISSUED && row.amountDueMinor <= 0L ->
                    Status(true, row.receivedAt, 0L, superseded = false, manual = false)
                else -> Status(false, null, null, superseded = false, manual = false)
            }
            bills.add(row)
        }

        var changed = 0
        for (b in bills) {
            val s = status.getValue(b.id)
            val differs = b.isPaid != s.paid || b.paidAt != s.paidAt || b.paidAmountMinor != s.paidMinor ||
                b.isSuperseded != s.superseded || b.paidManually != s.manual
            if (differs) {
                db.bills().update(
                    b.copy(
                        isPaid = s.paid,
                        paidAt = s.paidAt,
                        paidAmountMinor = s.paidMinor,
                        isSuperseded = s.superseded,
                        paidManually = s.manual,
                        updatedAt = ts,
                    ),
                )
                changed++
            }
        }
        return changed
    }

    /** Zero-amount rows stored only so a folded reminder is never applied twice. */
    private fun isShadow(row: BillEntity): Boolean =
        row.amountDueMinor == 0L && row.isPaid && !row.paidManually &&
            BillKind.fromId(row.kindId) != BillKind.ISSUED

    private fun billRow(bill: ParsedBill, hash: String, receivedAt: Long, ts: Long) = BillEntity(
        biller = bill.biller,
        accountRef = bill.accountRef,
        amountDueMinor = bill.amountDueMinor,
        currency = bill.currency,
        dueDate = bill.dueDateMillis,
        periodLabel = bill.periodLabel,
        kindId = bill.kind.id,
        rawBodyHash = hash,
        rawBody = bill.rawBody,
        receivedAt = receivedAt,
        createdAt = ts,
        updatedAt = ts,
    )

    /**
     * A reminder or receipt folded into an existing bill still needs its body hash recorded, or
     * the next inbox rescan would apply it again. Receipts keep their amount here: the replay
     * reads payments from these rows.
     */
    private fun shadowRow(bill: ParsedBill, hash: String, receivedAt: Long, ts: Long) = BillEntity(
        biller = bill.biller,
        accountRef = bill.accountRef,
        amountDueMinor = 0L,
        currency = bill.currency,
        dueDate = null,
        periodLabel = null,
        kindId = bill.kind.id,
        isPaid = true,
        paidAt = receivedAt,
        paidAmountMinor = bill.paidAmountMinor,
        reminderStage = 2,
        rawBodyHash = hash,
        rawBody = bill.rawBody,
        receivedAt = receivedAt,
        createdAt = ts,
        updatedAt = ts,
    )

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray())
            .take(16).joinToString("") { "%02x".format(it) }

    private companion object {
        /** A payment within Rs 10 of the balance settles it; billers round. */
        const val SETTLE_TOLERANCE_MINOR = 1_000L
    }
}
