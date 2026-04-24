package lk.salli.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Oatmilk Latte + Atomic Orange palette.
 *
 * Warm cream paper, deep-ink text, an orange accent reserved for moments that need heat
 * (primary CTAs, the `primary` role, selected-state highlights). Cards sit on the oatmilk
 * cream so the app breathes slightly warmer than plain white/gray.
 *
 * Canonical hex values (enor.designs swatch the user shipped):
 *   Oatmilk Latte:  #EBEBDF  — card/surface container
 *   Atomic Orange:  #E9631A  — primary / active pill / accents
 */

// Core ink — near-black so large headlines don't feel like tar.
private val Ink = Color(0xFF0B0B0C)

// Three cream steps for page → card. The brighter step is still off-white so it plays
// nicely against the warm backdrop without glare.
private val PaperWarm = Color(0xFFF3ECDA)     // page background
private val Oatmilk = Color(0xFFEBEBDF)       // canonical Oatmilk Latte — cards / pills
private val PaperBright = Color(0xFFFFFBEF)   // brightest step for white-ish tx pills

// Atomic Orange + supporting peach for primaryContainer.
private val AtomicOrange = Color(0xFFE9631A)
private val OrangeContainer = Color(0xFFF7D4B5)
private val OnOrangeContainer = Color(0xFF4A1B04)

// Warm neutral ramp — mirror of the old gray scale shifted toward cream.
private val Warm100 = Color(0xFFE4E0CE)
private val Warm200 = Color(0xFFD5D1BF)
private val Warm400 = Color(0xFFA39E8B)
private val Warm500 = Color(0xFF77736A)
private val Warm600 = Color(0xFF53504A)

// Semantic accents. Expense = warm terracotta (lives with the orange instead of clashing).
// Income = muted forest, reads as "up good" against cream without fighting the orange.
private val ExpenseInk = Color(0xFFB33A2A)
private val IncomeInk = Color(0xFF2E6A48)

private val LightScheme = lightColorScheme(
    primary = AtomicOrange,
    onPrimary = PaperBright,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OnOrangeContainer,

    secondary = Warm600,
    onSecondary = PaperBright,
    secondaryContainer = Oatmilk,
    onSecondaryContainer = Ink,

    // Tertiary repurposed as the income accent — TrendTile reads it as "up good".
    tertiary = IncomeInk,
    onTertiary = PaperBright,
    tertiaryContainer = Oatmilk,
    onTertiaryContainer = Ink,

    error = ExpenseInk,
    onError = PaperBright,
    errorContainer = Oatmilk,
    onErrorContainer = ExpenseInk,

    background = PaperWarm,
    onBackground = Ink,
    surface = PaperWarm,
    onSurface = Ink,
    surfaceVariant = Oatmilk,
    onSurfaceVariant = Warm500,

    surfaceContainerLowest = PaperBright,
    surfaceContainerLow = PaperWarm,
    surfaceContainer = Oatmilk,
    surfaceContainerHigh = Warm100,
    surfaceContainerHighest = Warm200,
    surfaceDim = Warm100,
    surfaceBright = PaperBright,

    outline = Warm400,
    outlineVariant = Warm100,

    // Inverse slot still holds the ink-dark pill (bottom nav, Save button) — the orange is
    // reserved for `primary`. Two distinct "dark" anchors risk chaos, so we keep nav/FAB
    // dark-ink to stay calm and let the orange own the hero moments.
    inverseSurface = Ink,
    inverseOnSurface = PaperBright,
    inversePrimary = PaperBright,

    scrim = Color(0xFF000000),
    surfaceTint = Color.Transparent,
)

/*
 * Dark mode — Atomic Orange on an ink-teal `#1E2B2F`. Keeps the primary colour identical
 * to light mode so the brand reads as one continuous accent across both schemes; only the
 * paper changes from cream to ink-teal.
 */
private val DeepInk = Color(0xFF1E2B2F)                 // page background
private val DeepInkLow = Color(0xFF172226)              // deepest — below surface
private val DeepInkContainer = Color(0xFF263438)        // secondary surface (month pills)
private val DeepInkContainerHigh = Color(0xFF304045)    // cards / tx pills
private val DeepInkContainerHighest = Color(0xFF3A4B50) // elevated pill (nav top)
private val DeepInkOutline = Color(0xFF526367)
private val CreamOnInk = Color(0xFFF3ECDA)              // warm cream text
private val CreamMuted = Color(0xFFA8ADA2)
private val OrangeContainerDark = Color(0xFF7A2E0C)
private val OnOrangeDark = Color(0xFF2D0D00)

private val DarkScheme = darkColorScheme(
    primary = AtomicOrange,                 // same orange as light mode
    onPrimary = PaperBright,
    primaryContainer = OrangeContainerDark,
    onPrimaryContainer = Color(0xFFFFD3BE),

    secondary = CreamMuted,
    onSecondary = DeepInk,
    secondaryContainer = DeepInkContainer,
    onSecondaryContainer = CreamOnInk,

    // Income accent — mint-ish green that holds up on dark teal.
    tertiary = Color(0xFF7ED3A3),
    onTertiary = DeepInk,
    tertiaryContainer = DeepInkContainer,
    onTertiaryContainer = CreamOnInk,

    error = Color(0xFFFFB4A4),
    onError = DeepInk,
    errorContainer = DeepInkContainer,
    onErrorContainer = Color(0xFFFFB4A4),

    background = DeepInk,
    onBackground = CreamOnInk,
    surface = DeepInk,
    onSurface = CreamOnInk,
    surfaceVariant = DeepInkContainer,
    onSurfaceVariant = CreamMuted,

    surfaceContainerLowest = DeepInkLow,
    surfaceContainerLow = DeepInk,
    surfaceContainer = DeepInkContainer,
    surfaceContainerHigh = DeepInkContainerHigh,
    surfaceContainerHighest = DeepInkContainerHighest,
    surfaceDim = DeepInkLow,
    surfaceBright = DeepInkContainerHighest,

    outline = DeepInkOutline,
    outlineVariant = DeepInkContainer,

    // Orange owns the "dark anchor" in dark mode — nav pill + Save button + selected month
    // pill. Mirrors the light-mode inversion (where inverseSurface was ink).
    inverseSurface = AtomicOrange,
    inverseOnSurface = OnOrangeDark,
    inversePrimary = CreamOnInk,

    scrim = Color(0xFF000000),
    surfaceTint = Color.Transparent,
)

internal fun fallbackScheme(isDark: Boolean): ColorScheme =
    if (isDark) DarkScheme else LightScheme
