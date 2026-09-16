package lk.salli.design.components

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import lk.salli.design.theme.SalliSpacing
import lk.salli.design.theme.SalliTheme

/**
 * Every component in the kit ships a preview in both themes. Dark mode is a first-class
 * palette rather than an inversion, so "it looked fine in light" is not evidence of anything.
 *
 * The two `@Preview`s differ only by `uiMode`, and [PreviewFrame] resolves the theme from
 * `isSystemInDarkTheme()`, so one annotated function covers both.
 */
@Preview(name = "Light", showBackground = true, widthDp = 380)
@Preview(
    name = "Dark",
    showBackground = true,
    widthDp = 380,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
internal annotation class SalliPreview

@Composable
internal fun PreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    SalliTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.spacedBy(SalliSpacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SalliSpacing.screenGutter),
                content = content,
            )
        }
    }
}
