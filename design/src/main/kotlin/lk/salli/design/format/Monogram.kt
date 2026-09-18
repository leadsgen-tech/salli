package lk.salli.design.format

/**
 * Two letters that stand for a name: the first letters of the first two words, or the first
 * two letters of a single word. "Keells Super" → KS, "PickMe" → PI, "Bank Of Ceylon - BOC" →
 * BO. Null when the name has no letters to give, so callers fall back to a glyph.
 */
object Monogram {
    fun of(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val words = name.removePrefix("Declined · ")
            .split(' ', '-', '·', '/', '.', '_', ',', '*')
            .map { w -> w.filter(Char::isLetter) }
            .filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> null
            words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
            else -> words[0].take(2).uppercase()
        }
    }
}
