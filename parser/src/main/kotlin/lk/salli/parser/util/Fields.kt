package lk.salli.parser.util

/**
 * Field-anchored extraction for *provisional* templates.
 *
 * Templates built from real samples anchor the whole sentence (`^…$`). Templates built from
 * format evidence only know the field phrases a bank uses ("Auth Pmt LKR 1,250.00",
 * "Avl Bal LKR …"), not the exact sentence around them, so they pull each field independently
 * and let the dispatcher fall through to Unknown when the essentials are missing.
 */
internal object Fields {
    fun first(rx: Regex, text: String, group: Int = 1): String? =
        rx.find(text)?.groupValues?.getOrNull(group)?.trim()?.takeIf { it.isNotEmpty() }
}
