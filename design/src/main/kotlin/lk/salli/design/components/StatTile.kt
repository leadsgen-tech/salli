package lk.salli.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lk.salli.design.theme.SalliShapeTokens
import lk.salli.design.theme.SalliSpacing

/**
 * One fact: a quiet label and a number that carries the weight. `surfaceContainer`, 16 dp
 * radius, tabular figures so a row of tiles aligns on the decimal point.
 *
 * If a tile needs a sentence it isn't a stat tile — use a [ListRow].
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: SalliTone = SalliTone.NEUTRAL,
    onClick: (() -> Unit)? = null,
) {
    val valueColor = when (tone) {
        SalliTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
        else -> tone.colors().accent
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = SalliShapeTokens.tile,
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
    ) {
        Column(modifier = Modifier.padding(SalliSpacing.sm)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(SalliSpacing.xxs))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@SalliPreview
@Composable
private fun StatTilePreview() {
    PreviewFrame {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SalliSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatTile(
                label = "Safe today",
                value = "Rs 4,120",
                tone = SalliTone.POSITIVE,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Balance",
                value = "Rs 16,400",
                modifier = Modifier.weight(1f),
            )
        }
        StatTile(label = "Over budget by", value = "Rs 2,840", tone = SalliTone.NEGATIVE)
    }
}
