package lk.salli.app.ui

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import lk.salli.design.components.stage.SpringOdometer
import lk.salli.design.theme.SalliTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel-level check on the rolling number. The wheels hide their stacked digits from
 * semantics on purpose, so the only honest way to know a digit is on screen is to look.
 *
 * Regression: every wheel cell after the first used to be measured at zero height (a Column
 * inside a one-digit-tall box squeezes what does not fit), so any digit that rolled ended up
 * blank and a changed amount lost most of its numbers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SpringOdometerTest {

    // An empty rule plus a Robolectric-built activity: the rule's own launcher needs the
    // activity declared in the manifest, and the test manifest would ship in the debug APK.
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var activity: ComponentActivity

    private fun show(content: @Composable () -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent(content = content)
    }

    @Test
    fun `digits that rolled are still drawn once the wheels settle`() {
        var amount by mutableStateOf("Rs 111.11")
        compose.mainClock.autoAdvance = false
        show {
            SalliTheme(darkTheme = false) {
                Column {
                    SpringOdometer(text = amount, modifier = Modifier.testTag("rolling"))
                    // Robolectric's software canvas ignores the wheel clip, so keep the
                    // reference well out of the strip's spill.
                    Spacer(Modifier.height(240.dp))
                    SpringOdometer(text = "Rs 999.99", modifier = Modifier.testTag("static"))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()

        // Written through the rule so the change is delivered before the frames run; with a
        // paused clock, a bare write would sit undelivered until the next frame.
        compose.runOnIdle { amount = "Rs 999.99" }
        compose.waitForIdle()
        // Well past the spring's settle time; every wheel must be at rest on its target digit.
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        compose.onNodeWithTag("rolling").assertContentDescriptionEquals("Rs 999.99")

        val rolled = capture("rolling").inkPixels()
        val static = capture("static").inkPixels()

        assertThat(static).isGreaterThan(0)
        // Same text, same style: the rolled one may only differ by anti-aliasing noise.
        assertThat(rolled.toDouble()).isWithin(static * 0.03).of(static.toDouble())
    }

    @Test
    fun `a wheel mid-roll shows ink, not a gap`() {
        var amount by mutableStateOf("Rs 111.11")
        compose.mainClock.autoAdvance = false
        show {
            SalliTheme(darkTheme = false) {
                Column {
                    SpringOdometer(text = amount, modifier = Modifier.testTag("rolling"))
                    // Robolectric's software canvas ignores the wheel clip, so keep the
                    // reference well out of the strip's spill.
                    Spacer(Modifier.height(240.dp))
                    SpringOdometer(text = "Rs 999.99", modifier = Modifier.testTag("static"))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()

        compose.runOnIdle { amount = "Rs 999.99" }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.onNodeWithTag("rolling").assertContentDescriptionEquals("Rs 999.99")

        val rolled = capture("rolling").inkPixels()
        val static = capture("static").inkPixels()

        // Two digits share each window mid-roll, so the ink can be a little above or below the
        // resting amount, but a wheel that went blank would drop it by half.
        assertThat(rolled.toDouble()).isGreaterThan(static * 0.7)
    }

    /**
     * Draws the window into a bitmap by hand and crops to the tagged node. The test-library
     * `captureToImage` waits for a frame on the thread that would have to produce it, which
     * under Robolectric never comes.
     */
    private fun capture(tag: String): ImageBitmap {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val root = activity.window.decorView
        val whole = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(Canvas(whole)) }
        System.getenv("SALLI_SHOT_DIR")?.let { dir ->
            java.io.File(dir, "$tag.png").outputStream().use { whole.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return Bitmap.createBitmap(
            whole,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width.toInt(),
            bounds.height.toInt(),
        ).asImageBitmap()
    }

    /** Pixels that differ clearly from the top-left background pixel. */
    private fun ImageBitmap.inkPixels(): Int {
        val map = toPixelMap()
        val bg = map[0, 0]
        var count = 0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val p = map[x, y]
                val delta = kotlin.math.abs(p.red - bg.red) +
                    kotlin.math.abs(p.green - bg.green) +
                    kotlin.math.abs(p.blue - bg.blue)
                if (delta > 0.6f) count++
            }
        }
        return count
    }
}
