package lk.salli.domain

/**
 * How incoming SMS are turned into transactions. One knob, two modes — anything fancier and the
 * user has to think too hard.
 */
enum class ParseMode(val id: Int) {
    /** Regex templates per bank. Instant, deterministic, offline, works for covered banks only. */
    STANDARD(0),

    /** On-device Gemma model. Slower, broader coverage, needs a one-time 529 MB download. */
    AI(1);

    companion object {
        fun fromId(id: Int): ParseMode = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}
