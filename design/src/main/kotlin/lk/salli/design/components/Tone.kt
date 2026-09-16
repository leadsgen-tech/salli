package lk.salli.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.LocalSalliColors
import lk.salli.design.theme.SalliShapeTokens
import lk.salli.design.theme.SalliSpacing

/**
 * The five moods a piece of Salli UI can be in. Components take a [SalliTone] rather than two
 * colours, so "due soon" looks the same whether it's a pill on Home, a bar on Plan or a banner
 * on Activity — and so switching what "warning" means is one edit.
 */
enum class SalliTone {
    /** Nothing to say. Surface container, ink text. */
    NEUTRAL,

    /** Under pace, eligible today, new since last open. The lime. */
    POSITIVE,

    /** Hot pace, due soon, needs a look. */
    WARNING,

    /** Over budget, overdue, declined. */
    NEGATIVE,

    /** Money in. */
    INCOME,

    /** Own transfers, fees — moved, not spent. */
    TRANSFER,
}

/** A tone resolved against the current theme: a fill and the ink that stays legible on it. */
@Immutable
data class ToneColors(val container: Color, val content: Color, val accent: Color)

@Composable
@ReadOnlyComposable
fun SalliTone.colors(): ToneColors {
    val salli = LocalSalliColors.current
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        SalliTone.NEUTRAL -> ToneColors(
            container = scheme.surfaceContainerHigh,
            content = scheme.onSurface,
            accent = scheme.onSurface,
        )
        SalliTone.POSITIVE -> ToneColors(
            container = salli.positive,
            content = salli.onPositive,
            accent = salli.positive,
        )
        SalliTone.WARNING -> ToneColors(
            container = salli.warningContainer,
            content = salli.onWarningContainer,
            accent = salli.warning,
        )
        SalliTone.NEGATIVE -> ToneColors(
            container = salli.negativeContainer,
            content = salli.onNegativeContainer,
            accent = salli.negative,
        )
        SalliTone.INCOME -> ToneColors(
            container = salli.incomeContainer,
            content = salli.onIncomeContainer,
            accent = salli.income,
        )
        SalliTone.TRANSFER -> ToneColors(
            container = scheme.surfaceContainerHigh,
            content = salli.transfer,
            accent = salli.transfer,
        )
    }
}

/**
 * Small toned pill for a status word or a trailing amount — "Eligible today", "3 days",
 * "Over budget". Always one short phrase; if it needs two lines it isn't a pill.
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: SalliTone = SalliTone.NEUTRAL,
) {
    val colors = tone.colors()
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .background(colors.container, SalliShapeTokens.pill)
            .padding(horizontal = SalliSpacing.sm, vertical = SalliSpacing.xxs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.content,
        )
    }
}

@SalliPreview
@Composable
private fun StatusPillPreview() {
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(text = "Under pace", tone = SalliTone.POSITIVE)
            StatusPill(text = "3 days", tone = SalliTone.WARNING)
            StatusPill(text = "Overdue", tone = SalliTone.NEGATIVE)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(text = "Rs 12,400", tone = SalliTone.INCOME)
            StatusPill(text = "Own transfer", tone = SalliTone.TRANSFER)
            StatusPill(text = "2 to pay", tone = SalliTone.NEUTRAL)
        }
    }
}
