package com.unshoo.pixelmusic.presentation.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.compose.ui.graphics.Color
import com.unshoo.pixelmusic.R

enum class AppLauncherIcon(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val previewDrawableRes: Int,
    @DrawableRes val backgroundDrawableRes: Int,
    val backgroundStartColor: Color,
    val backgroundEndColor: Color,
    @DrawableRes val iconRes: Int,
    val aliasClassName: String,
    @StyleRes val splashThemeRes: Int
) {
    BLUE_PURPLE(
        id = "BLUE_PURPLE",
        titleRes = R.string.app_icon_blue_purple_title,
        descriptionRes = R.string.app_icon_blue_purple_desc,
        previewDrawableRes = R.drawable.ic_pixelmusic_blue_purple,
        backgroundDrawableRes = R.drawable.launcher_bg_blue_purple,
        backgroundStartColor = Color(0xFFF2F7FF),
        backgroundEndColor = Color(0xFFEDEEFF),
        iconRes = R.mipmap.ic_launcher_blue_purple,
        aliasClassName = "com.unshoo.pixelmusic.MainActivityBluePurple",
        splashThemeRes = R.style.Theme_App_Starting_BluePurple
    ),
    GRAPHITE(
        id = "GRAPHITE",
        titleRes = R.string.app_icon_graphite_title,
        descriptionRes = R.string.app_icon_graphite_desc,
        previewDrawableRes = R.drawable.ic_pixelmusic_graphite_gray,
        backgroundDrawableRes = R.drawable.launcher_bg_graphite,
        backgroundStartColor = Color(0xFFF1F2F4),
        backgroundEndColor = Color(0xFFD9DCE1),
        iconRes = R.mipmap.ic_launcher_graphite,
        aliasClassName = "com.unshoo.pixelmusic.MainActivityGraphite",
        splashThemeRes = R.style.Theme_App_Starting_Graphite
    ),
    ORANGE_YELLOW(
        id = "ORANGE_YELLOW",
        titleRes = R.string.app_icon_orange_yellow_title,
        descriptionRes = R.string.app_icon_orange_yellow_desc,
        previewDrawableRes = R.drawable.ic_pixelmusic_orange_yellow,
        backgroundDrawableRes = R.drawable.launcher_bg_orange_yellow,
        backgroundStartColor = Color(0xFFFFF8E4),
        backgroundEndColor = Color(0xFFFFF2D4),
        iconRes = R.mipmap.ic_launcher_orange_yellow,
        aliasClassName = "com.unshoo.pixelmusic.MainActivityOrangeYellow",
        splashThemeRes = R.style.Theme_App_Starting_OrangeYellow
    ),
    LIME_GREEN(
        id = "LIME_GREEN",
        titleRes = R.string.app_icon_lime_green_title,
        descriptionRes = R.string.app_icon_lime_green_desc,
        previewDrawableRes = R.drawable.ic_pixelmusic_lime_green,
        backgroundDrawableRes = R.drawable.launcher_bg_lime_green,
        backgroundStartColor = Color(0xFFF7FFE2),
        backgroundEndColor = Color(0xFFEFF9D4),
        iconRes = R.mipmap.ic_launcher_lime_green,
        aliasClassName = "com.unshoo.pixelmusic.MainActivityLimeGreen",
        splashThemeRes = R.style.Theme_App_Starting_LimeGreen
    ),
    BABY_PINK_PURPLE(
        id = "BABY_PINK_PURPLE",
        titleRes = R.string.app_icon_baby_pink_purple_title,
        descriptionRes = R.string.app_icon_baby_pink_purple_desc,
        previewDrawableRes = R.drawable.ic_pixelmusic_baby_pink_purple,
        backgroundDrawableRes = R.drawable.launcher_bg_baby_pink_purple,
        backgroundStartColor = Color(0xFFFFF5FA),
        backgroundEndColor = Color(0xFFF5EDFF),
        iconRes = R.mipmap.ic_launcher_baby_pink_purple,
        aliasClassName = "com.unshoo.pixelmusic.MainActivityBabyPinkPurple",
        splashThemeRes = R.style.Theme_App_Starting_BabyPinkPurple
    ),
    LIME_LEMON(
        id = "LIME_LEMON",
        titleRes = R.string.app_icon_lime_lemon_title,
        descriptionRes = R.string.app_icon_lime_lemon_desc,
        previewDrawableRes = R.drawable.ic_pixelmusic_lime_lemon_yellow,
        backgroundDrawableRes = R.drawable.launcher_bg_lime_lemon,
        backgroundStartColor = Color(0xFFFBFFE3),
        backgroundEndColor = Color(0xFFFFF9D7),
        iconRes = R.mipmap.ic_launcher_lime_lemon,
        aliasClassName = "com.unshoo.pixelmusic.MainActivityLimeLemon",
        splashThemeRes = R.style.Theme_App_Starting_LimeLemon
    );

    companion object {
        val DEFAULT = BLUE_PURPLE

        fun fromId(id: String?): AppLauncherIcon {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
