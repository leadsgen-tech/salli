package lk.salli.data.backup

import kotlinx.serialization.json.Json

/** JSON in, JSON out. Validation lives here so restore never touches the DB on a bad file. */
object BackupCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(document: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), document)

    /** @throws IllegalArgumentException when the file is not a Salli backup this app can read. */
    fun decode(text: String): BackupDocument {
        val doc = runCatching { json.decodeFromString(BackupDocument.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("Not a Salli backup file", it) }
        require(doc.format == BackupDocument.FORMAT) { "Not a Salli backup file" }
        require(doc.version <= BackupDocument.VERSION) {
            "This backup was made by a newer Salli (format ${doc.version}); update the app first"
        }
        return doc
    }
}
