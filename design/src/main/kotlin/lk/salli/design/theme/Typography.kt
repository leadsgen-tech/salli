package lk.salli.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import lk.salli.design.R

/**
 * Typography tuned to the Revolut-inspired brief.
 *
 * Display / headline styles use **Space Grotesk** (our free substitute for Aeonik Pro — the
 * closest open-source geometric grotesque) with weight 500 and aggressive negative tracking.
 * Body / label styles use **Inter** with weight 400 / 500 / 600 and positive tracking for
 * airy reading. Every style opts into tabular numerals so amount columns align.
 */

private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private const val TabularNum = "tnum, lnum"

private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun display(
    sizeSp: Float,
    lineHeightSp: Float = sizeSp,
    letterSpacingEm: Float,
    weight: FontWeight = FontWeight.Medium,
): TextStyle = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = letterSpacingEm.em,
    fontFeatureSettings = TabularNum,
    lineHeightStyle = TightLineHeight,
)

private fun body(
    sizeSp: Float,
    lineHeightSp: Float,
    letterSpacingEm: Float,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = letterSpacingEm.em,
    fontFeatureSettings = TabularNum,
)

internal val SalliTypography = Typography(
    // Billboard hero. -0.022em on 57sp ≈ Revolut's -2.72px/136px proportion.
    displayLarge = display(sizeSp = 57f, lineHeightSp = 60f, letterSpacingEm = -0.022f),
    displayMedium = display(sizeSp = 45f, lineHeightSp = 48f, letterSpacingEm = -0.020f),
    displaySmall = display(sizeSp = 36f, lineHeightSp = 40f, letterSpacingEm = -0.018f),

    headlineLarge = display(sizeSp = 32f, lineHeightSp = 38f, letterSpacingEm = -0.016f),
    headlineMedium = display(sizeSp = 28f, lineHeightSp = 34f, letterSpacingEm = -0.014f),
    headlineSmall = display(sizeSp = 24f, lineHeightSp = 30f, letterSpacingEm = -0.012f),

    titleLarge = display(sizeSp = 22f, lineHeightSp = 28f, letterSpacingEm = -0.010f),
    titleMedium = display(sizeSp = 18f, lineHeightSp = 24f, letterSpacingEm = -0.005f),
    titleSmall = display(sizeSp = 15f, lineHeightSp = 20f, letterSpacingEm = 0f),

    // Inter + positive tracking — Revolut's +0.16–0.24px scaled to em.
    bodyLarge = body(sizeSp = 16f, lineHeightSp = 24f, letterSpacingEm = 0.015f),
    bodyMedium = body(sizeSp = 14f, lineHeightSp = 20f, letterSpacingEm = 0.012f),
    bodySmall = body(sizeSp = 12f, lineHeightSp = 16f, letterSpacingEm = 0.015f),

    labelLarge = body(sizeSp = 14f, lineHeightSp = 20f, letterSpacingEm = 0.007f, weight = FontWeight.Medium),
    labelMedium = body(sizeSp = 12f, lineHeightSp = 16f, letterSpacingEm = 0.040f, weight = FontWeight.Medium),
    labelSmall = body(sizeSp = 11f, lineHeightSp = 16f, letterSpacingEm = 0.045f, weight = FontWeight.Medium),
)
