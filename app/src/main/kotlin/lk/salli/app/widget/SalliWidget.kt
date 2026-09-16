package lk.salli.app.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.CancellationException
import lk.salli.app.MainActivity
import lk.salli.data.widget.WidgetSummary
import lk.salli.data.widget.WidgetSummaryService
import lk.salli.design.format.MoneyFormat

/**
 * Home-screen widget. Small (about 2×1): spent today and this period. Medium (about 4×2): the
 * same plus safe to spend today. Every number comes from [WidgetSummaryService]; this class only
 * lays them out. Tapping anywhere opens the app (which shows the lock screen when app lock is on).
 */
class SalliWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val service = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .widgetSummaryService()
        // Every update is a one-shot snapshot. Database/settings invalidations call updateAll,
        // so responsive sizes share this single query instead of each collecting another flow.
        val summary = try {
            service.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Couldn't load widget summary", error)
            null
        }
        provideContent {
            GlanceTheme(colors = WidgetColors) {
                WidgetContent(context, summary)
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 40.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)

        /** Re-renders every placed Salli widget. No-op when none are placed. */
        suspend fun refresh(context: Context) {
            try {
                SalliWidget().updateAll(context.applicationContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Couldn't refresh widgets", error)
                throw error
            }
        }

        private const val TAG = "SalliWidget"
    }
}

class SalliWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SalliWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        widgetEntryPoint(context).widgetUpdateCoordinator().start()
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        widgetEntryPoint(context).widgetUpdateCoordinator().stop()
        WidgetRefreshWorker.cancel(context)
        super.onDisabled(context)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetSummaryService(): WidgetSummaryService
    fun widgetUpdateCoordinator(): WidgetUpdateCoordinator
}

private fun widgetEntryPoint(context: Context): WidgetEntryPoint = EntryPointAccessors
    .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

private val WidgetColors = ColorProviders(
    light = lightColorScheme(
        primary = Color(0xFF003DFF),
        onPrimary = Color.White,
        tertiaryContainer = Color(0xFFDFFF32),
        onTertiaryContainer = Color(0xFF111407),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0A0D14),
        onSurfaceVariant = Color(0xFF596170),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF91A5FF),
        onPrimary = Color(0xFF001A66),
        tertiaryContainer = Color(0xFFDFFF32),
        onTertiaryContainer = Color(0xFF111407),
        surface = Color(0xFF111723),
        onSurface = Color(0xFFF4F6FC),
        onSurfaceVariant = Color(0xFFB8C0CF),
    ),
)

@Composable
private fun WidgetContent(context: Context, summary: WidgetSummary?) {
    val size = LocalSize.current
    val medium = size.width >= SalliWidget.MEDIUM.width && size.height >= SalliWidget.MEDIUM.height
    Column(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(
                actionStartActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    },
                ),
            ),
    ) {
        when {
            summary == null -> Text(text = "Salli", style = valueStyle(14))
            medium -> MediumLayout(summary)
            else -> SmallLayout(summary)
        }
    }
}

private val format: (lk.salli.domain.Money) -> String = { MoneyFormat.format(it) }

@Composable
private fun SmallLayout(summary: WidgetSummary) {
    LabelValueRow("Spent today", summary.spentTodayText(format))
    Spacer(GlanceModifier.height(2.dp))
    LabelValueRow("This period", summary.periodSpentText(format))
}

@Composable
private fun LabelValueRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        Text(text = label, style = labelStyle(), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Text(text = value, style = valueStyle(13), maxLines = 1)
    }
}

@Composable
private fun MediumLayout(summary: WidgetSummary) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Metric("Spent today", summary.spentTodayText(format), GlanceModifier.defaultWeight())
        Metric("This period", summary.periodSpentText(format), GlanceModifier.defaultWeight())
    }
    Spacer(GlanceModifier.height(8.dp))
    Metric(
        "Safe to spend today",
        summary.safeToSpendTodayText(format),
        GlanceModifier.fillMaxWidth(),
        emphasised = true,
    )
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: GlanceModifier,
    emphasised: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(text = label, style = labelStyle(), maxLines = 1)
        Text(
            text = value,
            style = valueStyle(17, emphasised),
            maxLines = 1,
        )
    }
}

@Composable
private fun labelStyle() = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)

@Composable
private fun valueStyle(sizeSp: Int, emphasised: Boolean = false) =
    TextStyle(
        color = if (emphasised) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Medium,
    )
