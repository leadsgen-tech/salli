package lk.salli.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import lk.salli.design.theme.fallbackScheme
import lk.salli.data.upcoming.UpcomingItem
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget. Small (about 2×1): spent today and this period. Medium (about 4×2): the
 * same plus safe to spend today. Every number comes from [WidgetSummaryService]; this class only
 * lays them out. Tapping anywhere opens the app (which shows the lock screen when app lock is on).
 */
class SalliWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, SMALL, MEDIUM))

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
        val upcoming = try {
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .upcomingService().observe().first().firstOrNull()
        } catch (_: Exception) {
            null
        }
        provideContent {
            GlanceTheme(colors = WidgetColors) {
                WidgetContent(context, summary, upcoming)
            }
        }
    }

    companion object {
        val COMPACT = DpSize(110.dp, 40.dp)
        val SMALL = DpSize(110.dp, 110.dp)
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
    fun upcomingService(): lk.salli.data.upcoming.UpcomingService
    fun widgetUpdateCoordinator(): WidgetUpdateCoordinator
}

private fun widgetEntryPoint(context: Context): WidgetEntryPoint = EntryPointAccessors
    .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

private val WidgetColors = ColorProviders(
    light = fallbackScheme(isDark = false),
    dark = fallbackScheme(isDark = true),
)

@Composable
private fun WidgetContent(context: Context, summary: WidgetSummary?, upcoming: UpcomingItem?) {
    val size = LocalSize.current
    val compact = size.height < 80.dp
    val medium = !compact && size.width >= SalliWidget.MEDIUM.width
    Column(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(actionStartActivity(homeIntent(context))),
    ) {
        when {
            summary == null -> Text(text = "Salli", style = valueStyle(14))
            compact -> CompactLayout(summary)
            medium -> MediumLayout(context, summary, upcoming)
            else -> SmallLayout(summary)
        }
    }
}

private val format: (lk.salli.domain.Money) -> String = { MoneyFormat.format(it) }

@Composable
private fun SmallLayout(summary: WidgetSummary) {
    Text("Spent today", style = labelStyle(), maxLines = 1)
    Text(summary.spentTodayText(format), style = valueStyle(24, true), maxLines = 1)
    // Glance does not expose Compose Canvas. This compact glyph preserves the pace signal at
    // launcher scale without a network or custom font dependency.
    Image(provider = ImageProvider(paceBitmap(summary)), contentDescription = "Spending pace", modifier = GlanceModifier.fillMaxWidth().height(6.dp))
    Text(summary.periodSpentText(format) + " spent · " + summary.daysRemaining + " days left", style = labelStyle(), maxLines = 1)
}

@Composable
private fun CompactLayout(summary: WidgetSummary) {
    val amount = summary.safeToSpendTodayMinor?.let { summary.safeToSpendTodayText(format) } ?: summary.spentTodayText(format)
    val label = if (summary.safeToSpendTodayMinor != null) "Safe today" else "Spent today"
    Text(label + " · " + amount, style = valueStyle(13, true), maxLines = 1)
}

@Composable
private fun LabelValueRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
        Text(text = label, style = labelStyle(), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Text(text = value, style = valueStyle(13), maxLines = 1)
    }
}

@Composable
private fun MediumLayout(context: Context, summary: WidgetSummary, upcoming: UpcomingItem?) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            SmallLayout(summary)
        }
        Column(modifier = GlanceModifier.defaultWeight().padding(start = 10.dp).clickable(actionStartActivity(planIntent(context)))) {
            Text("Up next", style = labelStyle(), maxLines = 1)
            if (upcoming == null) Text("Nothing due soon", style = valueStyle(13), maxLines = 2)
            else Text(upcoming.title + (upcoming.amountMinor?.let { " · " + MoneyFormat.format(lk.salli.domain.Money(it, upcoming.currency ?: "LKR")) } ?: ""), style = valueStyle(13), maxLines = 2)
        }
    }
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

private fun homeIntent(context: Context) = Intent(context, MainActivity::class.java).apply {
    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

private fun planIntent(context: Context) = Intent(context, MainActivity::class.java).apply {
    putExtra(MainActivity.EXTRA_START_ROUTE, "plan")
}

/** Tiny, data-driven pace bitmap: fill is period spending / optional budget limit. */
private fun paceBitmap(summary: WidgetSummary): Bitmap {
    val width = 160; val height = 8
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fraction = (summary.budgetLimitMinor?.takeIf { it > 0L }
        ?.let { summary.periodSpentMinor.toFloat() / it } ?: 0f).coerceIn(0f, 1f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.rgb(213, 218, 229)
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 4f, 4f, paint)
    paint.color = android.graphics.Color.rgb(0, 61, 255)
    canvas.drawRoundRect(0f, 0f, width * fraction, height.toFloat(), 4f, 4f, paint)
    return bitmap
}
