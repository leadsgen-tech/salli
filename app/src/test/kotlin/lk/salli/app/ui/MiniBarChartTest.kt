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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import lk.salli.design.components.MiniBarChart
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
 * The month-end projection is a dashed outline continuing the running month's bar. A Canvas
 * has no semantics to assert on, so this looks at the pixels: with a ghost the top of the
 * last slot carries ink, without one it is empty.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class MiniBarChartTest {

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
    fun `a projected month draws a dashed ghost above its bar`() {
        val values = listOf(40L, 60L, 30L, 50L, 45L, 20L).map { it * 100_000L }
        compose.mainClock.autoAdvance = false
        show {
            SalliTheme(darkTheme = false) {
                Column {
                    MiniBarChart(
                        values = values,
                        highlight = 5,
                        height = 56.dp,
                        ghosts = listOf(null, null, null, null, null, 70L * 100_000L),
                        modifier = Modifier.testTag("ghost"),
                    )
                    Spacer(Modifier.height(40.dp))
                    MiniBarChart(values = values, highlight = 5, height = 56.dp, modifier = Modifier.testTag("plain"))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        // Past the grow spring: both charts at full height.
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()

        val ghost = capture("ghost")
        val plain = capture("plain")
        // The last slot, upper 40 % of the chart: the 20-of-70 bar never reaches it, the ghost does.
        fun topOfLastSlot(image: ImageBitmap): Int = image.inkIn(
            x0 = image.width * 5 / 6 + 4, x1 = image.width - 4, y0 = 0, y1 = image.height * 2 / 5,
        )
        assertThat(topOfLastSlot(plain)).isEqualTo(0)
        assertThat(topOfLastSlot(ghost)).isGreaterThan(40)
        // Dashed, not filled: the ghost region is mostly background.
        val region = (ghost.width / 6 - 8) * (ghost.height * 2 / 5)
        assertThat(topOfLastSlot(ghost)).isLessThan(region / 2)
    }

    private fun capture(tag: String): ImageBitmap {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val root = activity.window.decorView
        val whole = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(Canvas(whole)) }
        System.getenv("SALLI_SHOT_DIR")?.let { dir ->
            java.io.File(dir, "$tag.png").outputStream().use { whole.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return Bitmap.createBitmap(whole, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt()).asImageBitmap()
    }

    /** Pixels inside the box that differ clearly from the chart's top-left background pixel. */
    private fun ImageBitmap.inkIn(x0: Int, x1: Int, y0: Int, y1: Int): Int {
        val map = toPixelMap()
        val bg = map[0, 0]
        var count = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val p = map[x, y]
                val delta = kotlin.math.abs(p.red - bg.red) + kotlin.math.abs(p.green - bg.green) + kotlin.math.abs(p.blue - bg.blue)
                if (delta > 0.6f) count++
            }
        }
        return count
    }
}
