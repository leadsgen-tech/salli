package lk.salli.data.ingest

import androidx.room.withTransaction
import lk.salli.data.db.SalliDatabase
import lk.salli.data.mapper.toParsed
import lk.salli.domain.TransactionFlow
import lk.salli.parser.merge.InternalTransferDetector

/**
 * Dissolves transfer groups that the current fee rule would never have created.
 *
 * Earlier builds accepted any fee up to Rs 100 in either direction (a Rs 50 debit paired with
 * a Rs 0.99 reversal) and ignored counterparty names (a People's debit "to Sampath Bank"
 * paired with a same-size BOC credit). Both legs of a dissolved group go back to plain
 * expense/income so totals and the "own transfer" rows are honest again.
 * Idempotent and cheap (a few dozen rows), so it is safe to run on every app start.
 */
class TransferGroupRepair(private val db: SalliDatabase) {

    /** @return number of groups dissolved. */
    suspend fun run(): Int = db.withTransaction {
        var dissolved = 0
        for (group in db.transferGroups().all()) {
            val debit = db.transactions().byId(group.debitTxId)
            val credit = db.transactions().byId(group.creditTxId)
            // Paired legs are stored with flow TRANSFER; hand the detector the original
            // directions so it judges the pair exactly as it would at ingest time (fee rule,
            // window, and "does a named counterparty point at the other bank").
            val ok = debit != null && credit != null &&
                InternalTransferDetector.findCounterpart(
                    incoming = debit.toParsed().copy(flow = TransactionFlow.EXPENSE),
                    recent = listOf(credit.toParsed().copy(flow = TransactionFlow.INCOME)),
                ) != null
            if (ok) continue
            debit?.let { db.transactions().clearTransferGroup(it.id, TransactionFlow.EXPENSE.id) }
            credit?.let { db.transactions().clearTransferGroup(it.id, TransactionFlow.INCOME.id) }
            db.transferGroups().deleteById(group.id)
            dissolved++
        }
        dissolved
    }
}
