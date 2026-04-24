package lk.salli.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Parked SMS from bank senders we recognize but couldn't parse — surfaced to the user for
 * triage. When the user confirms it's a transaction, we'll add a template / ask for more
 * samples.
 */
@Entity(tableName = "unknown_sms")
data class UnknownSmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sender_address")
    val senderAddress: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    /** null | "ignored" | "template_added" | "classified_manually" */
    @ColumnInfo(name = "resolution")
    val resolution: String? = null,
)
