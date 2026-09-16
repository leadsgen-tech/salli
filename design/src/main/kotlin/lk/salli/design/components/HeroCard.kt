package lk.salli.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.LocalSalliColors
import lk.salli.design.theme.SalliShapeTokens
import lk.salli.design.theme.SalliSpacing

/**
 * The cobalt block at the top of a screen that answers its one question.
 *
 * Slot-based on purpose: eyebrow, hero amount, a meta row, then whatever the screen needs
 * (a [PaceBar], a divider and two figures, nothing). The card owns the colour, the radius and
 * the padding; it does not own the content, so Home and Plan can differ without forking it.
 *
 * Cobalt is the same hex in both themes, so [LocalContentColor] is pinned to white inside —
 * children can use plain `Text` without thinking about the scheme.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    containerColor: Color = LocalSalliColors.current.hero,
    contentColor: Color = LocalSalliColors.current.onHero,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = SalliShapeTokens.hero,
        modifier = modifier.fillMaxWidth(),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = Modifier.padding(SalliSpacing.lg),
                content = content,
            )
        }
    }
}

/** Small all-caps label above a hero number. The one place caps are allowed. */
@Composable
fun HeroEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.8f),
        modifier = modifier,
    )
}

/** Hairline + two-column footer inside a [HeroCard] ("Safe today" / "Balance"). */
@Composable
fun HeroSplit(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(SalliSpacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LocalContentColor.current.copy(alpha = 0.22f)),
        )
        Spacer(Modifier.height(SalliSpacing.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(SalliSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

/** One cell of a [HeroSplit]: quiet key, loud value. */
@Composable
fun HeroFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalContentColor.current,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.8f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
        )
    }
}

@SalliPreview
@Composable
private fun HeroCardPreview() {
    PreviewFrame {
        HeroCard {
            HeroEyebrow("Spent this period")
            Text(
                text = "Rs 84,200",
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(SalliSpacing.sm))
            PaceBar(
                progress = 0.46f,
                expected = 0.53f,
                tone = SalliTone.POSITIVE,
                trackColor = LocalContentColor.current.copy(alpha = 0.22f),
                tickColor = LocalContentColor.current,
            )
            Spacer(Modifier.height(SalliSpacing.xs))
            Text(
                text = "Under pace · 7% less than last period",
                style = MaterialTheme.typography.bodySmall,
            )
            HeroSplit {
                HeroFact(
                    label = "Safe today",
                    value = "Rs 4,120",
                    valueColor = LocalSalliColors.current.positive,
                    modifier = Modifier.weight(1f),
                )
                HeroFact(label = "Balance", value = "Rs 16,400", modifier = Modifier.weight(1f))
            }
        }
    }
}
