package lk.salli.data.ingest

/**
 * Outcome of processing a single SMS through the full pipeline. Exposed for logging /
 * diagnostics / historical-import progress reporting.
 */
sealed interface IngestResult {
    /** New transaction persisted. [transactionId] is the DB row ID. */
    data class Inserted(val transactionId: Long) : IngestResult

    /** New transaction persisted and paired with an existing counterpart as internal transfer. */
    data class Paired(val transactionId: Long, val counterpartId: Long, val groupId: Long) : IngestResult

    /** Incoming SMS merged into an existing record (PeoplesBank debit+confirm case). */
    data class Merged(val transactionId: Long) : IngestResult

    /** Exact re-send of a recent SMS — dropped. */
    data class Duplicate(val existingId: Long) : IngestResult

    /** OTP or other noise — dropped, no DB change. */
    data class Dropped(val reason: String) : IngestResult

    /** Known bank sender, unrecognised format — parked in the review queue. */
    data class Queued(val unknownId: Long) : IngestResult
}
