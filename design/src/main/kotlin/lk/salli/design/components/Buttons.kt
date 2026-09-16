package lk.salli.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.salli.design.motion.SalliMotionSpecs

private val ButtonHeight = 56.dp
private val RestCorner = 30.dp
private val PressedCorner = 16.dp

/**
 * The primary action. 56 dp tall, labelLarge bold, and the corners square off from 30 dp to
 * 16 dp while your finger is down — a press-morph generalised from the onboarding button,
 * because it's the cheapest way to make a flat button feel physical without a shadow.
 *
 * Honours reduced motion: with animations off the corners snap instead of springing.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) PressedCorner else RestCorner,
        animationSpec = SalliMotionSpecs.fastSpatial(),
        label = "primary-button-corner",
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ButtonHeight),
    ) {
        ButtonLabel(text = text, leadingIcon = leadingIcon)
    }
}

/** Secondary action — same geometry, outlined. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(RestCorner),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ButtonHeight),
    ) {
        ButtonLabel(text = text, leadingIcon = leadingIcon)
    }
}

/** Tertiary action — text only, no container, for "Skip" / "Not now" / "See all". */
@Composable
fun SalliTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ButtonLabel(text: String, leadingIcon: ImageVector?) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@SalliPreview
@Composable
private fun ButtonsPreview() {
    PreviewFrame {
        PrimaryButton(text = "Allow SMS access", onClick = {})
        SecondaryButton(text = "Scan past messages", onClick = {})
        SalliTextButton(text = "Skip for now", onClick = {})
        PrimaryButton(text = "Disabled", onClick = {}, enabled = false)
    }
}
