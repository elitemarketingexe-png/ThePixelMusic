package com.unshoo.pixelmusic.presentation.model

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import com.unshoo.pixelmusic.R

data class ThemePreset(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val lightColors: List<Color>,
    val darkColors: List<Color>
) {
    fun getColors(isDark: Boolean, context: Context): List<Color> {
        if (key == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val scheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                return listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.surfaceContainerHighest)
            }
        }
        return if (isDark) darkColors else lightColors
    }

    companion object {
        val ALL: List<ThemePreset> = listOf(
            ThemePreset(
                key = "DYNAMIC",
                titleRes = R.string.setcat_color_palette_dynamic,
                descriptionRes = R.string.setcat_color_palette_dynamic_desc,
                lightColors = listOf(Color(0xFFE5B800), Color(0xFFF1E3A1), Color(0xFFA4D7B5), Color(0xFF201C12)),
                darkColors = listOf(Color(0xFFFFE16E), Color(0xFFD2C6A3), Color(0xFFA9D0B3), Color(0xFF15130C))
            ),
            ThemePreset(
                key = "SAGE",
                titleRes = R.string.setcat_color_palette_sage,
                descriptionRes = R.string.setcat_color_palette_sage_desc,
                lightColors = listOf(Color(0xFF23764F), Color(0xFFB8F3CD), Color(0xFF8CD0DE), Color(0xFF101512)),
                darkColors = listOf(Color(0xFF7CDAA3), Color(0xFF1B5035), Color(0xFF8CD0DE), Color(0xFF101512))
            ),
            ThemePreset(
                key = "ORANGE",
                titleRes = R.string.setcat_color_palette_orange,
                descriptionRes = R.string.setcat_color_palette_orange_desc,
                lightColors = listOf(Color(0xFF914E22), Color(0xFFFFD9C5), Color(0xFFE3C38A), Color(0xFF161210)),
                darkColors = listOf(Color(0xFFF2A77A), Color(0xFF6F3612), Color(0xFFE3C38A), Color(0xFF161210))
            ),
            ThemePreset(
                key = "PURPLE",
                titleRes = R.string.setcat_color_palette_purple,
                descriptionRes = R.string.setcat_color_palette_purple_desc,
                lightColors = listOf(Color(0xFF782CAF), Color(0xFFF7D2FB), Color(0xFFEDB1BF), Color(0xFF141015)),
                darkColors = listOf(Color(0xFFD29FD7), Color(0xFF782CAF), Color(0xFFEDB1BF), Color(0xFF141015))
            ),
            ThemePreset(
                key = "BLUE",
                titleRes = R.string.setcat_color_palette_blue,
                descriptionRes = R.string.setcat_color_palette_blue_desc,
                lightColors = listOf(Color(0xFF1E649B), Color(0xFFC9E2FF), Color(0xFFC4B6E6), Color(0xFF0E1317)),
                darkColors = listOf(Color(0xFF89BDEE), Color(0xFF154B78), Color(0xFFC4B6E6), Color(0xFF0E1317))
            ),
            ThemePreset(
                key = "YELLOW",
                titleRes = R.string.setcat_color_palette_yellow,
                descriptionRes = R.string.setcat_color_palette_yellow_desc,
                lightColors = listOf(Color(0xFF6E5A04), Color(0xFFFADE78), Color(0xFFA1D1AA), Color(0xFF14120C)),
                darkColors = listOf(Color(0xFFDCBC4E), Color(0xFF574600), Color(0xFFA1D1AA), Color(0xFF14120C))
            ),
            ThemePreset(
                key = "GREY",
                titleRes = R.string.setcat_color_palette_grey,
                descriptionRes = R.string.setcat_color_palette_grey_desc,
                lightColors = listOf(Color(0xFF191C1E), Color(0xFFCCD0DA), Color(0xFFE2E2E6), Color(0xFF111315)),
                darkColors = listOf(Color(0xFFE2E2E6), Color(0xFF454850), Color(0xFF8E9099), Color(0xFF111315))
            )
        )

        fun fromKey(key: String?): ThemePreset =
            ALL.find { it.key.equals(key, ignoreCase = true) } ?: ALL[1]
    }
}
