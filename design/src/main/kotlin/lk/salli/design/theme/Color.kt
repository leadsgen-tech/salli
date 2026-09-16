package lk.salli.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Stable brand colours for surfaces whose meaning is the same in both themes. */
object SalliBrandColors {
    val Cobalt = Color(0xFF003DFF)
    val OnCobalt = Color(0xFFFFFFFF)
    val AcidLime = Color(0xFFDFFF32)
    val OnAcidLime = Color(0xFF111407)
}

private val Ink = Color(0xFF0A0D14)
private val InkMuted = Color(0xFF596170)
private val CoolBackground = Color(0xFFF7F8FC)
private val White = Color(0xFFFFFFFF)
private val Cool050 = Color(0xFFF2F4F9)
private val Cool100 = Color(0xFFEAEDF5)
private val Cool200 = Color(0xFFDDE2EC)
private val Cool300 = Color(0xFFCBD2DF)
private val Cool500 = Color(0xFF737D8E)

private val CobaltSoft = Color(0xFFDCE5FF)
private val OnCobaltSoft = Color(0xFF00174F)
private val IncomeGreen = Color(0xFF087A55)
private val ExpenseRed = Color(0xFFB3261E)

private val LightScheme = lightColorScheme(
    primary = SalliBrandColors.Cobalt,
    onPrimary = SalliBrandColors.OnCobalt,
    primaryContainer = CobaltSoft,
    onPrimaryContainer = OnCobaltSoft,
    secondary = Color(0xFF252B37),
    onSecondary = White,
    secondaryContainer = Cool100,
    onSecondaryContainer = Ink,
    // Tertiary is the accessible income colour. Acid lime is a container accent and always
    // pairs with near-black text, including in dark mode.
    tertiary = IncomeGreen,
    onTertiary = White,
    tertiaryContainer = SalliBrandColors.AcidLime,
    onTertiaryContainer = SalliBrandColors.OnAcidLime,
    error = ExpenseRed,
    onError = White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = CoolBackground,
    onBackground = Ink,
    surface = CoolBackground,
    onSurface = Ink,
    surfaceVariant = Cool100,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = White,
    surfaceContainerLow = CoolBackground,
    surfaceContainer = Cool050,
    surfaceContainerHigh = Cool100,
    surfaceContainerHighest = Cool200,
    surfaceDim = Cool200,
    surfaceBright = White,
    outline = Cool500,
    outlineVariant = Cool300,
    inverseSurface = Ink,
    inverseOnSurface = White,
    inversePrimary = Color(0xFF9EB0FF),
    scrim = Color.Black,
    surfaceTint = Color.Transparent,
)

private val DarkBackground = Color(0xFF0A0E17)
private val DarkLowest = Color(0xFF070A11)
private val DarkSurface = Color(0xFF111723)
private val DarkSurfaceHigh = Color(0xFF182131)
private val DarkSurfaceHighest = Color(0xFF222D40)
private val DarkOutline = Color(0xFF8B95A8)
private val DarkOnSurface = Color(0xFFF4F6FC)
private val DarkMuted = Color(0xFFB8C0CF)
private val DarkPrimary = Color(0xFF91A5FF)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF001A66),
    primaryContainer = Color(0xFF173AAE),
    onPrimaryContainer = Color(0xFFE0E6FF),
    secondary = Color(0xFFC4CAD6),
    onSecondary = Color(0xFF252B35),
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = DarkOnSurface,
    tertiary = Color(0xFF72D5AD),
    onTertiary = Color(0xFF003826),
    tertiaryContainer = SalliBrandColors.AcidLime,
    onTertiaryContainer = SalliBrandColors.OnAcidLime,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = DarkLowest,
    surfaceContainerLow = DarkBackground,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    surfaceDim = DarkLowest,
    surfaceBright = DarkSurfaceHighest,
    outline = DarkOutline,
    outlineVariant = Color(0xFF3B4658),
    inverseSurface = DarkOnSurface,
    inverseOnSurface = Ink,
    inversePrimary = SalliBrandColors.Cobalt,
    scrim = Color.Black,
    surfaceTint = Color.Transparent,
)

internal fun fallbackScheme(isDark: Boolean): ColorScheme =
    if (isDark) DarkScheme else LightScheme
