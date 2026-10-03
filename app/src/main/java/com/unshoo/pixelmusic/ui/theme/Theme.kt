@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.unshoo.pixelmusic.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.graphics.ColorUtils
import com.unshoo.pixelmusic.presentation.viewmodel.ColorSchemePair

val LocalPixelMusicDarkTheme = staticCompositionLocalOf { false }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
fun PixelMusicStatusBarStyle(
    color: Color,
    useDarkIcons: Boolean = ColorUtils.calculateLuminance(color.toArgb()) > 0.55,
    navigationColor: Color? = null,
    useDarkNavigationIcons: Boolean = navigationColor
        ?.let { ColorUtils.calculateLuminance(it.toArgb()) > 0.55 }
        ?: useDarkIcons
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val updateNavigationBar = navigationColor != null
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = useDarkIcons

            if (updateNavigationBar) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                isAppearanceLightNavigationBars = useDarkNavigationIcons
            }
        }
    }
}

// --- Sage Green Palette (Inspired by Lime Green App Icon & Reference Image) ---
val SageDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7CDAA3),
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF1B5035),
    onPrimaryContainer = Color(0xFFB8F3CD),
    secondary = Color(0xFFAFC9B8),
    onSecondary = Color(0xFF1B3427),
    secondaryContainer = Color(0xFF324B3D),
    onSecondaryContainer = Color(0xFFCBE6D4),
    tertiary = Color(0xFF8CD0DE),
    onTertiary = Color(0xFF00363E),
    tertiaryContainer = Color(0xFF194D56),
    onTertiaryContainer = Color(0xFFB2E4EF),
    background = Color(0xFF101512),
    onBackground = Color(0xFFDEE3DF),
    surface = Color(0xFF101512),
    onSurface = Color(0xFFDEE3DF),
    surfaceVariant = Color(0xFF3E4943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF3E4943),
    surfaceTint = Color(0xFF7CDAA3),
    inverseSurface = Color(0xFFDEE3DF),
    inverseOnSurface = Color(0xFF181D1A),
    inversePrimary = Color(0xFF23764F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceBright = Color(0xFF353B37),
    surfaceDim = Color(0xFF101512),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF161C19),
    surfaceContainer = Color(0xFF1A201D),
    surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF2F3632)
)

val SageLightColorScheme = lightColorScheme(
    primary = Color(0xFF23764F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F3CD),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4C6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE7D6),
    onSecondaryContainer = Color(0xFF092015),
    tertiary = Color(0xFF33656E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBE8F2),
    onTertiaryContainer = Color(0xFF001F25),
    background = Color(0xFFF6FAF7),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF6FAF7),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF3E4943),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFC0C9C2),
    surfaceTint = Color(0xFF23764F),
    inverseSurface = Color(0xFF2D322E),
    inverseOnSurface = Color(0xFFEEF2ED),
    inversePrimary = Color(0xFF7CDAA3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD6DBD6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F1),
    surfaceContainer = Color(0xFFEAF0EB),
    surfaceContainerHigh = Color(0xFFE4EAE5),
    surfaceContainerHighest = Color(0xFFDEE4DF)
)

val DarkColorScheme = SageDarkColorScheme
val LightColorScheme = SageLightColorScheme

// --- Sunset Orange Palette (Inspired by Orange Yellow App Icon - Soothing Amber Terracotta) ---
val OrangeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF2A77A),
    onPrimary = Color(0xFF492208),
    primaryContainer = Color(0xFF6F3612),
    onPrimaryContainer = Color(0xFFFFD9C5),
    secondary = Color(0xFFDAC0B2),
    onSecondary = Color(0xFF3D2B21),
    secondaryContainer = Color(0xFF544136),
    onSecondaryContainer = Color(0xFFF7DCD0),
    tertiary = Color(0xFFE3C38A),
    onTertiary = Color(0xFF3C2E06),
    tertiaryContainer = Color(0xFF53441B),
    onTertiaryContainer = Color(0xFFF6DBA6),
    background = Color(0xFF161210),
    onBackground = Color(0xFFEFE0D9),
    surface = Color(0xFF161210),
    onSurface = Color(0xFFEFE0D9),
    surfaceVariant = Color(0xFF4D443F),
    onSurfaceVariant = Color(0xFFD1C3BC),
    outline = Color(0xFF998D86),
    outlineVariant = Color(0xFF4D443F),
    surfaceTint = Color(0xFFF2A77A),
    inverseSurface = Color(0xFFEFE0D9),
    inverseOnSurface = Color(0xFF1F1A17),
    inversePrimary = Color(0xFF914E22),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceBright = Color(0xFF3C3532),
    surfaceDim = Color(0xFF161210),
    surfaceContainerLowest = Color(0xFF100D0B),
    surfaceContainerLow = Color(0xFF1C1715),
    surfaceContainer = Color(0xFF211B19),
    surfaceContainerHigh = Color(0xFF2B2523),
    surfaceContainerHighest = Color(0xFF372F2D)
)

val OrangeLightColorScheme = lightColorScheme(
    primary = Color(0xFF914E22),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9C5),
    onPrimaryContainer = Color(0xFF331302),
    secondary = Color(0xFF6F584C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7DCD0),
    onSecondaryContainer = Color(0xFF27170E),
    tertiary = Color(0xFF6C5A30),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6DBA6),
    onTertiaryContainer = Color(0xFF241A00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF211A17),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF211A17),
    surfaceVariant = Color(0xFFF2DFD6),
    onSurfaceVariant = Color(0xFF4D443F),
    outline = Color(0xFF81746E),
    outlineVariant = Color(0xFFD1C3BC),
    surfaceTint = Color(0xFF914E22),
    inverseSurface = Color(0xFF362E2B),
    inverseOnSurface = Color(0xFFFAEDE8),
    inversePrimary = Color(0xFFF2A77A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5DDD8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EB),
    surfaceContainer = Color(0xFFFAEAE4),
    surfaceContainerHigh = Color(0xFFF4E4DE),
    surfaceContainerHighest = Color(0xFFEEDFD8)
)

// --- Purple Pink Palette (Inspired by Baby Pink Purple App Icon - Soothing Orchid & Baby Pink) ---
val PurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD29FD7),
    onPrimary = Color(0xFF3E1A43),
    primaryContainer = Color(0xFF782CAF),
    onPrimaryContainer = Color(0xFFF7D2FB),
    secondary = Color(0xFFCCBCCF),
    onSecondary = Color(0xFF342636),
    secondaryContainer = Color(0xFF4B3C4D),
    onSecondaryContainer = Color(0xFFE8D8EB),
    tertiary = Color(0xFFEDB1BF),
    onTertiary = Color(0xFF451E28),
    tertiaryContainer = Color(0xFF5E343E),
    onTertiaryContainer = Color(0xFFFFD9E0),
    background = Color(0xFF141015),
    onBackground = Color(0xFFE7DFE6),
    surface = Color(0xFF141015),
    onSurface = Color(0xFFE7DFE6),
    surfaceVariant = Color(0xFF4A424B),
    onSurfaceVariant = Color(0xFFCCC2CD),
    outline = Color(0xFF968C97),
    outlineVariant = Color(0xFF4A424B),
    surfaceTint = Color(0xFFD29FD7),
    inverseSurface = Color(0xFFE7DFE6),
    inverseOnSurface = Color(0xFF1D181D),
    inversePrimary = Color(0xFF782CAF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceBright = Color(0xFF3B353B),
    surfaceDim = Color(0xFF141015),
    surfaceContainerLowest = Color(0xFF0F0B10),
    surfaceContainerLow = Color(0xFF1A151B),
    surfaceContainer = Color(0xFF1E1920),
    surfaceContainerHigh = Color(0xFF29232A),
    surfaceContainerHighest = Color(0xFF342D36)
)

val PurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF782CAF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF7D2FB),
    onPrimaryContainer = Color(0xFF2C0734),
    secondary = Color(0xFF655467),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8D8EB),
    onSecondaryContainer = Color(0xFF211323),
    tertiary = Color(0xFF784954),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E0),
    onTertiaryContainer = Color(0xFF2E0A14),
    background = Color(0xFFFCF7FC),
    onBackground = Color(0xFF1E181E),
    surface = Color(0xFFFCF7FC),
    onSurface = Color(0xFF1E181E),
    surfaceVariant = Color(0xFFEAE0EB),
    onSurfaceVariant = Color(0xFF4A424B),
    outline = Color(0xFF7C727D),
    outlineVariant = Color(0xFFCCC2CD),
    surfaceTint = Color(0xFF782CAF),
    inverseSurface = Color(0xFF332D33),
    inverseOnSurface = Color(0xFFF7EFF7),
    inversePrimary = Color(0xFFD29FD7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE0D7E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7EFF7),
    surfaceContainer = Color(0xFFF1E9F2),
    surfaceContainerHigh = Color(0xFFECE3ED),
    surfaceContainerHighest = Color(0xFFE6DDE7)
)

// --- Ocean Blue Palette (Inspired by Blue Purple App Icon - Soothing Ocean & Periwinkle) ---
val BlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF89BDEE),
    onPrimary = Color(0xFF003050),
    primaryContainer = Color(0xFF154B78),
    onPrimaryContainer = Color(0xFFC9E2FF),
    secondary = Color(0xFFB1C4D7),
    onSecondary = Color(0xFF1B2F40),
    secondaryContainer = Color(0xFF324657),
    onSecondaryContainer = Color(0xFFCEE0F4),
    tertiary = Color(0xFFC4B6E6),
    onTertiary = Color(0xFF282143),
    tertiaryContainer = Color(0xFF3E375A),
    onTertiaryContainer = Color(0xFFE2D9FF),
    background = Color(0xFF0E1317),
    onBackground = Color(0xFFDDE2E8),
    surface = Color(0xFF0E1317),
    onSurface = Color(0xFFDDE2E8),
    surfaceVariant = Color(0xFF3F464D),
    onSurfaceVariant = Color(0xFFBFC6CE),
    outline = Color(0xFF8A9198),
    outlineVariant = Color(0xFF3F464D),
    surfaceTint = Color(0xFF89BDEE),
    inverseSurface = Color(0xFFDDE2E8),
    inverseOnSurface = Color(0xFF171C20),
    inversePrimary = Color(0xFF1E649B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceBright = Color(0xFF34393E),
    surfaceDim = Color(0xFF0E1317),
    surfaceContainerLowest = Color(0xFF090E11),
    surfaceContainerLow = Color(0xFF141A1F),
    surfaceContainer = Color(0xFF181F24),
    surfaceContainerHigh = Color(0xFF23292F),
    surfaceContainerHighest = Color(0xFF2D343A)
)

val BlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF1E649B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9E2FF),
    onPrimaryContainer = Color(0xFF001C34),
    secondary = Color(0xFF4A5F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE0F4),
    onSecondaryContainer = Color(0xFF061C2C),
    tertiary = Color(0xFF564F73),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2D9FF),
    onTertiaryContainer = Color(0xFF140D2D),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF171C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF171C20),
    surfaceVariant = Color(0xFFDBE2EA),
    onSurfaceVariant = Color(0xFF3F464D),
    outline = Color(0xFF70777F),
    outlineVariant = Color(0xFFBFC6CE),
    surfaceTint = Color(0xFF1E649B),
    inverseSurface = Color(0xFF2C3135),
    inverseOnSurface = Color(0xFFEEF1F6),
    inversePrimary = Color(0xFF89BDEE),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD6DADF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFEAEEF3),
    surfaceContainerHigh = Color(0xFFE4E9EE),
    surfaceContainerHighest = Color(0xFFDEE3E8)
)

// --- Sunny Yellow Palette (Inspired by Lime Lemon Yellow App Icon - Soothing Warm Gold) ---
val YellowDarkColorScheme = darkColorScheme(
    primary = Color(0xFFDCBC4E),
    onPrimary = Color(0xFF372D00),
    primaryContainer = Color(0xFF574600),
    onPrimaryContainer = Color(0xFFFADE78),
    secondary = Color(0xFFCBBFA4),
    onSecondary = Color(0xFF332B16),
    secondaryContainer = Color(0xFF4A422B),
    onSecondaryContainer = Color(0xFFE8DCBF),
    tertiary = Color(0xFFA1D1AA),
    onTertiary = Color(0xFF0C351B),
    tertiaryContainer = Color(0xFF254C30),
    onTertiaryContainer = Color(0xFFBAE5C2),
    background = Color(0xFF14120C),
    onBackground = Color(0xFFE6E2D8),
    surface = Color(0xFF14120C),
    onSurface = Color(0xFFE6E2D8),
    surfaceVariant = Color(0xFF48453B),
    onSurfaceVariant = Color(0xFFC9C5B7),
    outline = Color(0xFF939082),
    outlineVariant = Color(0xFF48453B),
    surfaceTint = Color(0xFFDCBC4E),
    inverseSurface = Color(0xFFE6E2D8),
    inverseOnSurface = Color(0xFF1D1B13),
    inversePrimary = Color(0xFF6E5A04),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceBright = Color(0xFF3A3831),
    surfaceDim = Color(0xFF14120C),
    surfaceContainerLowest = Color(0xFF0F0D07),
    surfaceContainerLow = Color(0xFF1B1912),
    surfaceContainer = Color(0xFF1F1C16),
    surfaceContainerHigh = Color(0xFF2A2720),
    surfaceContainerHighest = Color(0xFF35322A)
)

val YellowLightColorScheme = lightColorScheme(
    primary = Color(0xFF6E5A04),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFADE78),
    onPrimaryContainer = Color(0xFF201900),
    secondary = Color(0xFF635A43),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DCBF),
    onSecondaryContainer = Color(0xFF1F1805),
    tertiary = Color(0xFF3B6445),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBAE5C2),
    onTertiaryContainer = Color(0xFF00210B),
    background = Color(0xFFFCF9F0),
    onBackground = Color(0xFF1D1B13),
    surface = Color(0xFFFCF9F0),
    onSurface = Color(0xFF1D1B13),
    surfaceVariant = Color(0xFFE7E2D3),
    onSurfaceVariant = Color(0xFF48453B),
    outline = Color(0xFF7A7669),
    outlineVariant = Color(0xFFC9C5B7),
    surfaceTint = Color(0xFF6E5A04),
    inverseSurface = Color(0xFF323027),
    inverseOnSurface = Color(0xFFF6F0E4),
    inversePrimary = Color(0xFFDCBC4E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDFDACF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F3EA),
    surfaceContainer = Color(0xFFF0EDE4),
    surfaceContainerHigh = Color(0xFFEAE7DF),
    surfaceContainerHighest = Color(0xFFE4E1D9)
)

// --- Dark & Grey Palette (Official Material 3 Monochrome/Neutral) ---
private val GreyDarkBackground = Color(0xFF111315)
private val GreyDarkSurface = Color(0xFF111315)
private val GreyDarkSurfaceVariant = Color(0xFF44464B)
private val GreyDarkPrimary = Color(0xFFE2E2E6)
private val GreyDarkOnPrimary = Color(0xFF191C1E)
private val GreyDarkPrimaryContainer = Color(0xFF454850)
private val GreyDarkOnPrimaryContainer = Color(0xFFF2F2F6)
private val GreyDarkSecondary = Color(0xFFC6C6CB)
private val GreyDarkOnSecondary = Color(0xFF2E3034)
private val GreyDarkSecondaryContainer = Color(0xFF3B3E46)
private val GreyDarkOnSecondaryContainer = Color(0xFFE2E2E6)
private val GreyDarkTertiary = Color(0xFFA9ADB5)
private val GreyDarkOnTertiary = Color(0xFF24272D)
private val GreyDarkTertiaryContainer = Color(0xFF383C43)
private val GreyDarkOnTertiaryContainer = Color(0xFFDFE2EA)
private val GreyDarkOnBackground = Color(0xFFE2E2E6)
private val GreyDarkOnSurface = Color(0xFFE2E2E6)
private val GreyDarkOnSurfaceVariant = Color(0xFFC4C6D0)
private val GreyDarkOutline = Color(0xFF8E9099)
private val GreyDarkOutlineVariant = Color(0xFF44474E)

private val GreyLightBackground = Color(0xFFF8F9FA)
private val GreyLightSurface = Color(0xFFFCFCFF)
private val GreyLightPrimary = Color(0xFF191C1E)
private val GreyLightOnPrimary = Color(0xFFFFFFFF)
private val GreyLightPrimaryContainer = Color(0xFFCCD0DA)
private val GreyLightOnPrimaryContainer = Color(0xFF111418)
private val GreyLightSecondary = Color(0xFF5C5E62)
private val GreyLightOnSecondary = Color(0xFFFFFFFF)
private val GreyLightSecondaryContainer = Color(0xFFD6DAE4)
private val GreyLightOnSecondaryContainer = Color(0xFF15181C)
private val GreyLightTertiary = Color(0xFF555D67)
private val GreyLightOnTertiary = Color(0xFFFFFFFF)
private val GreyLightTertiaryContainer = Color(0xFFD6DBE5)
private val GreyLightOnTertiaryContainer = Color(0xFF111B24)
private val GreyLightOnBackground = Color(0xFF191C1E)
private val GreyLightOnSurface = Color(0xFF191C1E)
private val GreyLightSurfaceVariant = Color(0xFFDFE2E8)
private val GreyLightOnSurfaceVariant = Color(0xFF44474E)
private val GreyLightOutline = Color(0xFF74777F)
private val GreyLightOutlineVariant = Color(0xFFC4C7CF)

val GreyDarkColorScheme = darkColorScheme(
    primary = GreyDarkPrimary,
    onPrimary = GreyDarkOnPrimary,
    primaryContainer = GreyDarkPrimaryContainer,
    onPrimaryContainer = GreyDarkOnPrimaryContainer,
    secondary = GreyDarkSecondary,
    onSecondary = GreyDarkOnSecondary,
    secondaryContainer = GreyDarkSecondaryContainer,
    onSecondaryContainer = GreyDarkOnSecondaryContainer,
    tertiary = GreyDarkTertiary,
    onTertiary = GreyDarkOnTertiary,
    tertiaryContainer = GreyDarkTertiaryContainer,
    onTertiaryContainer = GreyDarkOnTertiaryContainer,
    background = GreyDarkBackground,
    onBackground = GreyDarkOnBackground,
    surface = GreyDarkSurface,
    onSurface = GreyDarkOnSurface,
    surfaceVariant = GreyDarkSurfaceVariant,
    onSurfaceVariant = GreyDarkOnSurfaceVariant,
    outline = GreyDarkOutline,
    outlineVariant = GreyDarkOutlineVariant,
    surfaceTint = GreyDarkPrimary,
    inversePrimary = Color(0xFF5B5E63),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF1D2024),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceBright = Color(0xFF383A3D),
    surfaceDim = Color(0xFF111315),
    surfaceContainer = Color(0xFF22252B),
    surfaceContainerLowest = Color(0xFF0C0E10),
    surfaceContainerLow = Color(0xFF181A1E),
    surfaceContainerHigh = Color(0xFF2A2D34),
    surfaceContainerHighest = Color(0xFF33363E)
)

val GreyLightColorScheme = lightColorScheme(
    primary = GreyLightPrimary,
    onPrimary = GreyLightOnPrimary,
    primaryContainer = GreyLightPrimaryContainer,
    onPrimaryContainer = GreyLightOnPrimaryContainer,
    secondary = GreyLightSecondary,
    onSecondary = GreyLightOnSecondary,
    secondaryContainer = GreyLightSecondaryContainer,
    onSecondaryContainer = GreyLightOnSecondaryContainer,
    tertiary = GreyLightTertiary,
    onTertiary = GreyLightOnTertiary,
    tertiaryContainer = GreyLightTertiaryContainer,
    onTertiaryContainer = GreyLightOnTertiaryContainer,
    background = GreyLightBackground,
    onBackground = GreyLightOnBackground,
    surface = GreyLightSurface,
    onSurface = GreyLightOnSurface,
    surfaceVariant = GreyLightSurfaceVariant,
    onSurfaceVariant = GreyLightOnSurfaceVariant,
    outline = GreyLightOutline,
    outlineVariant = GreyLightOutlineVariant,
    surfaceTint = GreyLightPrimary,
    inversePrimary = Color(0xFFC6C6CA),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F1F5),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD9D9DE),
    surfaceContainer = Color(0xFFE9EBEF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4F8),
    surfaceContainerHigh = Color(0xFFE2E4EA),
    surfaceContainerHighest = Color(0xFFDBDEE5)
)

fun getStaticColorScheme(palette: String, darkTheme: Boolean): androidx.compose.material3.ColorScheme {
    return if (darkTheme) {
        when (palette) {
            "PURPLE" -> PurpleDarkColorScheme
            "BLUE" -> BlueDarkColorScheme
            "ORANGE" -> OrangeDarkColorScheme
            "YELLOW" -> YellowDarkColorScheme
            "GREY" -> GreyDarkColorScheme
            else -> SageDarkColorScheme
        }
    } else {
        when (palette) {
            "PURPLE" -> PurpleLightColorScheme
            "BLUE" -> BlueLightColorScheme
            "ORANGE" -> OrangeLightColorScheme
            "YELLOW" -> YellowLightColorScheme
            "GREY" -> GreyLightColorScheme
            else -> SageLightColorScheme
        }
    }
}

@Composable
fun PixelMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    colorSchemePairOverride: ColorSchemePair? = null,
    colorPalette: String = "SAGE",
    useSystemFont: Boolean = false,
    pitchBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    // BUGFIX (was: side-effect on every recomposition): the previous code
    // unconditionally wrote `FontSettings.useSystemFont = useSystemFont`
    // at the top of the function. Since this Composable is called on every
    // activity composition, that's a State mutation in the render path —
    // it forces recomposition of every consumer of `FontSettings`. We now
    // do it inside a LaunchedEffect keyed on `useSystemFont` so it only
    // runs when the value actually changes.
    androidx.compose.runtime.LaunchedEffect(useSystemFont) {
        if (FontSettings.useSystemFont != useSystemFont) {
            FontSettings.useSystemFont = useSystemFont
        }
    }
    val context = LocalContext.current
    // BUGFIX (recompose on every call): `getStaticColorScheme` and
    // `dynamicLight/DarkColorScheme` allocate a new ColorScheme (which
    // contains ~30+ Color values) on every call. We memoize the result
    // keyed on the inputs that actually affect the colors, so a
    // recomposition that doesn't change palette/darkTheme/dynamicColor
    // reuses the same ColorScheme instance — Compose then skips the
    // re-invalidation of all theme consumers.
    val baseColorScheme = androidx.compose.runtime.remember(
        colorSchemePairOverride, darkTheme, dynamicColor, colorPalette
    ) {
        when {
            colorSchemePairOverride != null -> {
                if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
            }
            colorPalette == "DYNAMIC" && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                try {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } catch (e: Exception) {
                    getStaticColorScheme(colorPalette, darkTheme)
                }
            }
            else -> {
                getStaticColorScheme(colorPalette, darkTheme)
            }
        }
    }

    val finalColorScheme = androidx.compose.runtime.remember(
        baseColorScheme, darkTheme, pitchBlack
    ) {
        if (darkTheme && pitchBlack) {
            baseColorScheme.copy(
                background = Color.Black,
                surface = Color(0xFF0C0E10),
                surfaceVariant = Color(0xFF1E2024),
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0F1113),
                surfaceContainer = Color(0xFF141619),
                surfaceContainerHigh = Color(0xFF1C1E22),
                surfaceContainerHighest = Color(0xFF24272C),
                surfaceDim = Color.Black,
                surfaceBright = Color(0xFF2C2F34),
                outlineVariant = Color(0xFF2A2D33),
                outline = Color(0xFF5A5D64)
            )
        } else {
            baseColorScheme
        }
    }

    PixelMusicStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    CompositionLocalProvider(LocalPixelMusicDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            shapes = Shapes,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}
