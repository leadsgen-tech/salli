package lk.salli.data.ingest

import androidx.room.withTransaction
import lk.salli.data.categorization.KeywordCategorizer
import lk.salli.data.categorization.TypeCategorizer
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.AccountEntity
import lk.salli.data.db.entities.TransferGroupEntity
import lk.salli.data.db.entities.UnknownSmsEntity
import lk.salli.data.mapper.toEntity
import lk.salli.data.mapper.toParsed
import lk.salli.domain.AccountType
import lk.salli.domain.TransactionFlow
import lk.salli.domain.TransactionMethod
import lk.salli.parser.ParseResult
import lk.salli.parser.ParsedTransaction
import lk.salli.parser.SmsParser
import lk.salli.parser.merge.DuplicateDetector
import lk.salli.parser.merge.InternalTransferDetector
import lk.salli.parser.merge.PeoplesBankMerger

/**
 * End-to-end SMS → DB pipeline.
 *
 *  1. [SmsParser.parse] classifies the body.
 *  2. For successes: [DuplicateDetector] drops re-sends, [PeoplesBankMerger] folds
 *     debit+confirm pairs, [InternalTransferDetector] pairs cross-bank transfers.
 *  3. Accounts are upserted by (sender, suffix); balance fields are updated in-place.
 *  4. Unknown SMS from known bank senders land in [UnknownSmsEntity] for user triage.
 *
 * Thread-safety: each call runs inside a Room transaction so partial DB states can't leak.
 */
class TransactionIngestor(
    private val db: SalliDatabase,
    private val categorizer: KeywordCategorizer,
    private val typeCategorizer: TypeCategorizer,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val method: TransactionMethod = TransactionMethod.SMS,
) {

    suspend fun ingest(sender: String, body: String, receivedAt: Long): IngestResult {
        val parse = SmsParser.parse(sender, body, receivedAt)
        return when (parse) {
            is ParseResult.Otp -> IngestResult.Dropped("otp")
            is ParseResult.Informational -> IngestResult.Dropped("informational: ${parse.reason}")
            is ParseResult.Unknown -> {
                val id = db.unknownSms().insert(
                    UnknownSmsEntity(
                        senderAddress = parse.sender,
                        body = parse.body,
                        receivedAt = receivedAt,
                    ),
                )
                IngestResult.Queued(id)
            }
            is ParseResult.Success -> persist(parse.tx)
        }
    }

    private suspend fun persist(parsed: ParsedTransaction): IngestResult = db.withTransaction {
        val dupWindowStart = parsed.timestamp - DuplicateDetector.WINDOW_MS
        val recentEntities = db.transactions()
            .recentFromSender(parsed.senderAddress, dupWindowStart)

        // Body-identity short-circuit. If we've already ingested this exact SMS within the
        // window, we're done — covers re-scans (pull-to-refresh, second historical import)
        // cleanly without depending on how the parser's projection compares across merge
        // states. Real bank re-sends can still have the same body and will correctly resolve
        // to the same stored row.
        val bodyKey = parsed.rawBody.trim()
        if (bodyKey.isNotEmpty()) {
            val byBody = recentEntities.firstOrNull { it.rawBody?.trim() == bodyKey }
            if (byBody != null) return@withTransaction IngestResult.Duplicate(existingId = byBody.id)
        }

        val recentFromSender = recentEntities
            .map { it.toParsed().copy(rawBody = it.rawBody.orEmpty()) }

        // Duplicate check first — cheapest exit.
        val dupByParsed = DuplicateDetector.findDuplicate(parsed, recentFromSender)
        if (dupByParsed != null) {
            // Resolve back to the DB row. Using raw_body identity isn't reliable, so we
            // re-query for the matching entity by amount+balance+timestamp.
            val parsedBalanceMinor = parsed.balance?.minorUnits
            val match = recentEntities.firstOrNull { e ->
                e.amountMinor == parsed.amount.minorUnits &&
                    e.amountCurrency == parsed.amount.currency &&
                    e.accountSuffix == parsed.accountNumberSuffix &&
                    kotlin.math.abs(e.timestamp - parsed.timestamp) <= DuplicateDetector.WINDOW_MS &&
                    (parsedBalanceMinor == null || e.balanceMinor == parsedBalanceMinor)
            }
            return@withTransaction IngestResult.Duplicate(existingId = match?.id ?: -1L)
        }

        // PeoplesBank merge.
        val mergeWindowStart = parsed.timestamp - PeoplesBankMerger.WINDOW_MS
        val recentPeoples = db.transactions()
            .recentFromSender("PeoplesBank", mergeWindowStart)
        val recentPeoplesParsed = recentPeoples.map { it.toParsed() }
        val merge = PeoplesBankMerger.tryMerge(parsed, recentPeoplesParsed)
        if (merge != null) {
            val existingEntity = recentPeoples.firstOrNull { entity ->
                val asParsed = entity.toParsed()
                asParsed.amount == merge.supersedes.amount &&
                    asParsed.timestamp == merge.supersedes.timestamp &&
                    asParsed.merchantRaw == merge.supersedes.merchantRaw
            }
            if (existingEntity != null) {
                val mergedEntity = existingEntity.copy(
                    amountMinor = merge.merged.amount.minorUnits,
                    feeMinor = merge.merged.fee?.minorUnits,
                    balanceMinor = merge.merged.balance?.minorUnits ?: existingEntity.balanceMinor,
                    merchantRaw = merge.merged.merchantRaw,
                    typeId = merge.merged.type.id,
                    updatedAt = now(),
                )
                db.transactions().update(mergedEntity)
                // A merge can change the row's balance_minor (an orphan confirm had null; after
                // merging with its primary it suddenly carries a balance). The cached account
                // balance needs to re-pick the latest balance-carrying row.
                if (mergedEntity.balanceMinor != null) {
                    db.accounts().refreshCachedBalance(existingEntity.accountId)
                }
                return@withTransaction IngestResult.Merged(transactionId = existingEntity.id)
            }
        }

        // Normal insert path. Two-step categorisation: keyword match on merchantRaw first,
        // then a type-based fallback so transfers/cash/fees don't drop into Uncategorised
        // when they don't carry a merchant name.
        val accountId = upsertAccount(parsed)
        val keywordHit = categorizer.categorize(parsed.merchantRaw)
        val categoryIdFromType = if (keywordHit == null) {
            typeCategorizer.categorize(parsed.type, parsed.flow)
        } else null
        val nowMs = now()
        val entity = parsed.toEntity(accountId = accountId, method = method, nowMillis = nowMs)
            .copy(
                categoryId = keywordHit?.categoryId ?: categoryIdFromType,
                subCategoryId = keywordHit?.subCategoryId,
            )
        val txId = db.transactions().insert(entity)

        // Refresh account balance atomically from the latest-dated transaction that carries
        // one. A single UPDATE with a subquery avoids any read-then-write race, and picking by
        // body timestamp means out-of-order SMS delivery can't leave the cached balance stale.
        if (parsed.balance != null) {
            db.accounts().refreshCachedBalance(accountId)
        }

        // Cross-bank pairing — only for unflagged income/expense (not declined, not transfers).
        if (!parsed.isDeclined && (parsed.flow == TransactionFlow.EXPENSE || parsed.flow == TransactionFlow.INCOME)) {
            val transferWindowStart = parsed.timestamp - InternalTransferDetector.WINDOW_MS
            val recentAll = db.transactions().recentAll(transferWindowStart)
            val counterpartEntity = recentAll.firstOrNull { e ->
                e.id != txId &&
                    e.senderAddress != parsed.senderAddress &&
                    e.transferGroupId == null &&
                    e.isDeclined == false &&
                    run {
                        val other = e.toParsed()
                        val counterpart = InternalTransferDetector
                            .findCounterpart(parsed, listOf(other))
                        counterpart != null
                    }
            }
            if (counterpartEntity != null) {
                val (debitId, creditId) = if (parsed.flow == TransactionFlow.EXPENSE)
                    txId to counterpartEntity.id
                else
                    counterpartEntity.id to txId
                val groupId = db.transferGroups().insert(
                    TransferGroupEntity(
                        debitTxId = debitId,
                        creditTxId = creditId,
                        confidence = 1f,
                        createdAt = nowMs,
                    ),
                )
                db.transactions().assignTransferGroup(txId, groupId, TransactionFlow.TRANSFER.id)
                db.transactions().assignTransferGroup(counterpartEntity.id, groupId, TransactionFlow.TRANSFER.id)
                return@withTransaction IngestResult.Paired(
                    transactionId = txId,
                    counterpartId = counterpartEntity.id,
                    groupId = groupId,
                )
            }
        }

        IngestResult.Inserted(transactionId = txId)
    }

    private suspend fun upsertAccount(parsed: ParsedTransaction): Long {
        val suffix = parsed.accountNumberSuffix
        if (suffix != null) {
            // Explicit account identifier — normal path.
            val existing = db.accounts().findBySenderAndSuffix(parsed.senderAddress, suffix)
            if (existing != null) return existing.id
            // If a "—" placeholder for this sender exists (created earlier by an orphan
            // confirm SMS), promote it rather than creating a second row. That keeps the
            // orphan's transaction attached to the canonical account once we learn the real
            // suffix.
            val placeholder = db.accounts().findBySenderAndSuffix(parsed.senderAddress, DEFAULT_SUFFIX)
            if (placeholder != null) {
                val promoted = placeholder.copy(
                    accountSuffix = suffix,
                    displayName = "${parsed.senderAddress} ($suffix)",
                    currency = parsed.amount.currency,
                )
                db.accounts().update(promoted)
                return promoted.id
            }
            return db.accounts().insert(
                AccountEntity(
                    senderAddress = parsed.senderAddress,
                    accountSuffix = suffix,
                    displayName = "${parsed.senderAddress} ($suffix)",
                    currency = parsed.amount.currency,
                    accountTypeId = AccountType.UNKNOWN.id,
                ),
            )
        }
        // No suffix (orphan confirm). Reuse the most recently active account for this sender —
        // creating a separate placeholder would split one real account into two.
        db.accounts().mostRecentForSender(parsed.senderAddress)?.let { return it.id }
        // First-ever SMS from this sender and it happens to be an orphan confirm. Seed a
        // placeholder; a later primary SMS will adopt it via the promotion path above.
        val existing = db.accounts().findBySenderAndSuffix(parsed.senderAddress, DEFAULT_SUFFIX)
        if (existing != null) return existing.id
        return db.accounts().insert(
            AccountEntity(
                senderAddress = parsed.senderAddress,
                accountSuffix = DEFAULT_SUFFIX,
                displayName = parsed.senderAddress,
                currency = parsed.amount.currency,
                accountTypeId = AccountType.UNKNOWN.id,
            ),
        )
    }

    companion object {
        private const val DEFAULT_SUFFIX = "—"
    }
}
