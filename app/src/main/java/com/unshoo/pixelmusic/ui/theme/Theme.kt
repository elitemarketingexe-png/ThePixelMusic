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

// --- Sage Green / Mint Palette (Soothing) ---
private val SageDarkBackground = Color(0xFF0D1210)
private val SageDarkSurface = Color(0xFF151B18)
private val SageDarkPrimary = Color(0xFF6EDBB1)
private val SageDarkSecondary = Color(0xFF4E9A7E)
private val SageDarkTertiary = Color(0xFF8AC7AC)
private val SageDarkOnPrimary = Color(0xFF003824)
private val SageDarkOnBackground = Color(0xFFE1E3DF)
private val SageDarkOnSurface = Color(0xFFE1E3DF)
private val SageDarkOnSurfaceVariant = Color(0xFFBFC9C2)

private val SageLightBackground = Color(0xFFF3FAF6)
private val SageLightSurface = Color(0xFFF7FCFA)
private val SageLightPrimary = Color(0xFF1F6C50)
private val SageLightOnPrimary = Color(0xFFFFFFFF)
private val SageLightPrimaryContainer = Color(0xFFC4F2DB)
private val SageLightOnPrimaryContainer = Color(0xFF002114)
private val SageLightSecondary = Color(0xFF4C6357)
private val SageLightSecondaryContainer = Color(0xFFCEE9DB)
private val SageLightOnSecondaryContainer = Color(0xFF092016)
private val SageLightTertiary = Color(0xFF3F6555)
private val SageLightOnBackground = Color(0xFF191D1A)
private val SageLightOnSurface = Color(0xFF191D1A)
private val SageLightSurfaceVariant = Color(0xFFDCE5DE)
private val SageLightOnSurfaceVariant = Color(0xFF404944)
private val SageLightOutline = Color(0xFF707973)

val SageDarkColorScheme = darkColorScheme(
    primary = SageDarkPrimary,
    secondary = SageDarkSecondary,
    tertiary = SageDarkTertiary,
    background = SageDarkBackground,
    surface = SageDarkSurface,
    onPrimary = SageDarkOnPrimary,
    onSecondary = SageDarkOnPrimary,
    onTertiary = SageDarkOnPrimary,
    onBackground = SageDarkOnBackground,
    onSurface = SageDarkOnSurface,
    onSurfaceVariant = SageDarkOnSurfaceVariant,
    error = Color(0xFFFF5252),
    onError = Color.White
)

val SageLightColorScheme = lightColorScheme(
    primary = SageLightPrimary,
    onPrimary = SageLightOnPrimary,
    primaryContainer = SageLightPrimaryContainer,
    onPrimaryContainer = SageLightOnPrimaryContainer,
    secondary = SageLightSecondary,
    onSecondary = SageLightOnPrimary,
    secondaryContainer = SageLightSecondaryContainer,
    onSecondaryContainer = SageLightOnSecondaryContainer,
    tertiary = SageLightTertiary,
    onTertiary = PixelMusicBlack,
    background = SageLightBackground,
    onBackground = SageLightOnBackground,
    surface = SageLightSurface,
    onSurface = SageLightOnSurface,
    surfaceVariant = SageLightSurfaceVariant,
    onSurfaceVariant = SageLightOnSurfaceVariant,
    outline = SageLightOutline,
    outlineVariant = SageLightOutline.copy(alpha = 0.6f),
    surfaceTint = SageLightPrimary,
    error = Color(0xFFD32F2F),
    onError = Color.White
)

// Standard default schemas mapped to Sage to make soothing green the out-of-the-box default
val DarkColorScheme = SageDarkColorScheme
val LightColorScheme = SageLightColorScheme

// --- Classic Purple Palette ---
private val PurpleDarkBackground = Color(0xFF0A0714)
private val PurpleDarkSurface = Color(0xFF13101E)
private val PurpleDarkPrimary = Color(0xFFB29BF4)
private val PurpleDarkSecondary = Color(0xFFE57399)
private val PurpleDarkTertiary = Color(0xFFD4B2F7)
private val PurpleDarkOnPrimary = Color(0xFF280066)
private val PurpleDarkOnBackground = Color(0xFFE7E3EC)
private val PurpleDarkOnSurface = Color(0xFFE7E3EC)
private val PurpleDarkOnSurfaceVariant = Color(0xFFC9C4D0)

private val PurpleLightBackground = Color(0xFFF9F7FC)
private val PurpleLightSurface = Color(0xFFFAF9FC)
private val PurpleLightPrimary = Color(0xFF6C4FBB)
private val PurpleLightOnPrimary = Color(0xFFFFFFFF)
private val PurpleLightPrimaryContainer = Color(0xFFE9E3FB)
private val PurpleLightOnPrimaryContainer = Color(0xFF1F005C)
private val PurpleLightSecondary = Color(0xFF825272)
private val PurpleLightSecondaryContainer = Color(0xFFFFD8EC)
private val PurpleLightOnSecondaryContainer = Color(0xFF370B2C)
private val PurpleLightTertiary = Color(0xFF705574)
private val PurpleLightOnBackground = Color(0xFF1C1A22)
private val PurpleLightOnSurface = Color(0xFF1C1A22)
private val PurpleLightSurfaceVariant = Color(0xFFE7E0EC)
private val PurpleLightOnSurfaceVariant = Color(0xFF49454F)
private val PurpleLightOutline = Color(0xFF7A757F)

val PurpleDarkColorScheme = darkColorScheme(
    primary = PurpleDarkPrimary,
    secondary = PurpleDarkSecondary,
    tertiary = PurpleDarkTertiary,
    background = PurpleDarkBackground,
    surface = PurpleDarkSurface,
    onPrimary = PurpleDarkOnPrimary,
    onSecondary = PurpleDarkOnPrimary,
    onTertiary = PurpleDarkOnPrimary,
    onBackground = PurpleDarkOnBackground,
    onSurface = PurpleDarkOnSurface,
    onSurfaceVariant = PurpleDarkOnSurfaceVariant,
    error = Color(0xFFFF5252),
    onError = Color.White
)

val PurpleLightColorScheme = lightColorScheme(
    primary = PurpleLightPrimary,
    onPrimary = PurpleLightOnPrimary,
    primaryContainer = PurpleLightPrimaryContainer,
    onPrimaryContainer = PurpleLightOnPrimaryContainer,
    secondary = PurpleLightSecondary,
    onSecondary = PurpleLightOnPrimary,
    secondaryContainer = PurpleLightSecondaryContainer,
    onSecondaryContainer = PurpleLightOnSecondaryContainer,
    tertiary = PurpleLightTertiary,
    onTertiary = PixelMusicBlack,
    background = PurpleLightBackground,
    onBackground = PurpleLightOnBackground,
    surface = PurpleLightSurface,
    onSurface = PurpleLightOnSurface,
    surfaceVariant = PurpleLightSurfaceVariant,
    onSurfaceVariant = PurpleLightOnSurfaceVariant,
    outline = PurpleLightOutline,
    outlineVariant = PurpleLightOutline.copy(alpha = 0.6f),
    surfaceTint = PurpleLightPrimary,
    error = Color(0xFFD32F2F),
    onError = Color.White
)

// --- Slate Blue Palette ---
private val BlueDarkBackground = Color(0xFF0B0F14)
private val BlueDarkSurface = Color(0xFF12171E)
private val BlueDarkPrimary = Color(0xFF7DB0E6)
private val BlueDarkSecondary = Color(0xFF5A84B0)
private val BlueDarkTertiary = Color(0xFF8AB9E6)
private val BlueDarkOnPrimary = Color(0xFF00315C)
private val BlueDarkOnBackground = Color(0xFFE2E2E6)
private val BlueDarkOnSurface = Color(0xFFE2E2E6)
private val BlueDarkOnSurfaceVariant = Color(0xFFC2C7CF)

private val BlueLightBackground = Color(0xFFF3F7FA)
private val BlueLightSurface = Color(0xFFF7FAFC)
private val BlueLightPrimary = Color(0xFF22588F)
private val BlueLightOnPrimary = Color(0xFFFFFFFF)
private val BlueLightPrimaryContainer = Color(0xFFC4DEF6)
private val BlueLightOnPrimaryContainer = Color(0xFF001C3A)
private val BlueLightSecondary = Color(0xFF436080)
private val BlueLightSecondaryContainer = Color(0xFFC9E2FF)
private val BlueLightOnSecondaryContainer = Color(0xFF001D38)
private val BlueLightTertiary = Color(0xFF3E6080)
private val BlueLightOnBackground = Color(0xFF191C1E)
private val BlueLightOnSurface = Color(0xFF191C1E)
private val BlueLightSurfaceVariant = Color(0xFFDFE2E7)
private val BlueLightOnSurfaceVariant = Color(0xFF43474B)
private val BlueLightOutline = Color(0xFF73777C)

val BlueDarkColorScheme = darkColorScheme(
    primary = BlueDarkPrimary,
    secondary = BlueDarkSecondary,
    tertiary = BlueDarkTertiary,
    background = BlueDarkBackground,
    surface = BlueDarkSurface,
    onPrimary = BlueDarkOnPrimary,
    onSecondary = BlueDarkOnPrimary,
    onTertiary = BlueDarkOnPrimary,
    onBackground = BlueDarkOnBackground,
    onSurface = BlueDarkOnSurface,
    onSurfaceVariant = BlueDarkOnSurfaceVariant,
    error = Color(0xFFFF5252),
    onError = Color.White
)

val BlueLightColorScheme = lightColorScheme(
    primary = BlueLightPrimary,
    onPrimary = BlueLightOnPrimary,
    primaryContainer = BlueLightPrimaryContainer,
    onPrimaryContainer = BlueLightOnPrimaryContainer,
    secondary = BlueLightSecondary,
    onSecondary = BlueLightOnPrimary,
    secondaryContainer = BlueLightSecondaryContainer,
    onSecondaryContainer = BlueLightOnSecondaryContainer,
    tertiary = BlueLightTertiary,
    onTertiary = PixelMusicBlack,
    background = BlueLightBackground,
    onBackground = BlueLightOnBackground,
    surface = BlueLightSurface,
    onSurface = BlueLightOnSurface,
    surfaceVariant = BlueLightSurfaceVariant,
    onSurfaceVariant = BlueLightOnSurfaceVariant,
    outline = BlueLightOutline,
    outlineVariant = BlueLightOutline.copy(alpha = 0.6f),
    surfaceTint = BlueLightPrimary,
    error = Color(0xFFD32F2F),
    onError = Color.White
)

// --- Sunset Orange Palette ---
private val OrangeDarkBackground = Color(0xFF120E0A)
private val OrangeDarkSurface = Color(0xFF1A1510)
private val OrangeDarkPrimary = Color(0xFFF5A873)
private val OrangeDarkSecondary = Color(0xFFB37D56)
private val OrangeDarkTertiary = Color(0xFFF7BE98)
private val OrangeDarkOnPrimary = Color(0xFF4C1E00)
private val OrangeDarkOnBackground = Color(0xFFECE1DB)
private val OrangeDarkOnSurface = Color(0xFFECE1DB)
private val OrangeDarkOnSurfaceVariant = Color(0xFFD7C4B7)

private val OrangeLightBackground = Color(0xFFFAF6F2)
private val OrangeLightSurface = Color(0xFFFCFAF7)
private val OrangeLightPrimary = Color(0xFF8F4F20)
private val OrangeLightOnPrimary = Color(0xFFFFFFFF)
private val OrangeLightPrimaryContainer = Color(0xFFFADFC9)
private val OrangeLightOnPrimaryContainer = Color(0xFF341100)
private val OrangeLightSecondary = Color(0xFF7E5233)
private val OrangeLightSecondaryContainer = Color(0xFFFFDBC6)
private val OrangeLightOnSecondaryContainer = Color(0xFF301400)
private val OrangeLightTertiary = Color(0xFF79563C)
private val OrangeLightOnBackground = Color(0xFF221A15)
private val OrangeLightOnSurface = Color(0xFF221A15)
private val OrangeLightSurfaceVariant = Color(0xFFF4DFD0)
private val OrangeLightOnSurfaceVariant = Color(0xFF52443C)
private val OrangeLightOutline = Color(0xFF85736B)

val OrangeDarkColorScheme = darkColorScheme(
    primary = OrangeDarkPrimary,
    secondary = OrangeDarkSecondary,
    tertiary = OrangeDarkTertiary,
    background = OrangeDarkBackground,
    surface = OrangeDarkSurface,
    onPrimary = OrangeDarkOnPrimary,
    onSecondary = OrangeDarkOnPrimary,
    onTertiary = OrangeDarkOnPrimary,
    onBackground = OrangeDarkOnBackground,
    onSurface = OrangeDarkOnSurface,
    onSurfaceVariant = OrangeDarkOnSurfaceVariant,
    error = Color(0xFFFF5252),
    onError = Color.White
)

val OrangeLightColorScheme = lightColorScheme(
    primary = OrangeLightPrimary,
    onPrimary = OrangeLightOnPrimary,
    primaryContainer = OrangeLightPrimaryContainer,
    onPrimaryContainer = OrangeLightOnPrimaryContainer,
    secondary = OrangeLightSecondary,
    onSecondary = OrangeLightOnPrimary,
    secondaryContainer = OrangeLightSecondaryContainer,
    onSecondaryContainer = OrangeLightOnSecondaryContainer,
    tertiary = OrangeLightTertiary,
    onTertiary = PixelMusicBlack,
    background = OrangeLightBackground,
    onBackground = OrangeLightOnBackground,
    surface = OrangeLightSurface,
    onSurface = OrangeLightOnSurface,
    surfaceVariant = OrangeLightSurfaceVariant,
    onSurfaceVariant = OrangeLightOnSurfaceVariant,
    outline = OrangeLightOutline,
    outlineVariant = OrangeLightOutline.copy(alpha = 0.6f),
    surfaceTint = OrangeLightPrimary,
    error = Color(0xFFD32F2F),
    onError = Color.White
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
            "GREY" -> GreyDarkColorScheme
            else -> SageDarkColorScheme
        }
    } else {
        when (palette) {
            "PURPLE" -> PurpleLightColorScheme
            "BLUE" -> BlueLightColorScheme
            "ORANGE" -> OrangeLightColorScheme
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
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
