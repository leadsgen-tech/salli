package lk.salli.design.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing ladder: 4 · 8 · 12 · 16 · 20 · 24 · 32. Nothing in between.
 *
 * Three of them are named twice on purpose, because they're layout decisions rather than
 * arbitrary gaps and reading `SalliSpacing.screenGutter` at a call site says more than `20.dp`:
 *
 *   [screenGutter] 20 — every screen's left/right inset
 *   [sectionGap]   24 — vertical gap between two sections
 *   [cardPadding]  16 — internal padding of a card
 */
object SalliSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Screen left/right inset. */
    val screenGutter = lg

    /** Vertical gap between sections. */
    val sectionGap = xl

    /** Internal padding inside a card or grouped-list row. */
    val cardPadding = md
}
