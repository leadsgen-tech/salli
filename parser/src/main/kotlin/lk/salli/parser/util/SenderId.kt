package lk.salli.parser.util

/**
 * Canonical form of an SMS sender ID, applied once before anything matches on it.
 *
 * Android's SMS provider can return alphanumeric sender IDs with stray characters. On a real
 * Pixel every ComBank alert's address was `COMBANK` followed by a line break, so the exact match
 * `^COMBANK$` failed and months of ComBank alerts were dropped as "sender not registered" by both
 * the live receiver and every inbox re-scan. Removes control and invisible formatting characters
 * anywhere in the ID and trims surrounding whitespace, including no-break spaces.
 */
object SenderId {
    private val invisible = Regex("""[\p{Cc}\p{Cf}]""")

    fun normalize(raw: String): String = raw.replace(invisible, "").trim()
}
