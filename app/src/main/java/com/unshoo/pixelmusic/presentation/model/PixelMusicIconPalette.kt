package com.unshoo.pixelmusic.presentation.model

import androidx.compose.ui.graphics.Color

data class PixelMusicIconPalette(
    val shape4: List<Color>,
    val shape3: List<Color>,
    val shape2: List<Color>,
    val shape1: List<Color>,
    val note: Color = Color.White,
)

object PixelMusicIconPalettes {
    val BluePurple = PixelMusicIconPalette(
        shape4 = listOf(Color(0xFF9BCBFF), Color(0xFF72BCFF), Color(0xFF4EA6FF), Color(0xFF3789F2)),
        shape3 = listOf(Color(0xFFB6C9FF), Color(0xFF9EAFFF), Color(0xFF858EFF), Color(0xFF756FEA)),
        shape2 = listOf(Color(0xFF69B9FF), Color(0xFF4CA3FF), Color(0xFF318AF3), Color(0xFF256BD8)),
        shape1 = listOf(Color(0xFF3A91F7), Color(0xFF287CF0), Color(0xFF1D65D8), Color(0xFF164FB6)),
    )

    val GraphiteGray = PixelMusicIconPalette(
        shape4 = listOf(Color(0xFFD9DBE0), Color(0xFFC7CAD0), Color(0xFFB5B9C0), Color(0xFFA0A5AE)),
        shape3 = listOf(Color(0xFF9DA2AA), Color(0xFF858A94), Color(0xFF6D727D), Color(0xFF5A606A)),
        shape2 = listOf(Color(0xFF696E78), Color(0xFF555A64), Color(0xFF41464F), Color(0xFF30343B)),
        shape1 = listOf(Color(0xFF292B31), Color(0xFF1E2025), Color(0xFF15171B), Color(0xFF0C0D10)),
    )

    val OrangeYellow = PixelMusicIconPalette(
        shape4 = listOf(Color(0xFFFFE895), Color(0xFFFFDA69), Color(0xFFFFCA45), Color(0xFFF5B22A)),
        shape3 = listOf(Color(0xFFFFE06B), Color(0xFFFFD04A), Color(0xFFFFC13A), Color(0xFFEEA51F)),
        shape2 = listOf(Color(0xFFFFB83F), Color(0xFFFFA02C), Color(0xFFF1871D), Color(0xFFD96A13)),
        shape1 = listOf(Color(0xFFF27A24), Color(0xFFE86319), Color(0xFFCC4C12), Color(0xFFA9380D)),
    )

    val LimeGreen = PixelMusicIconPalette(
        shape4 = listOf(Color(0xFFE9FF91), Color(0xFFD9FA68), Color(0xFFC2EE45), Color(0xFFA8D936)),
        shape3 = listOf(Color(0xFFD8F95E), Color(0xFFC6EE42), Color(0xFFADDF2D), Color(0xFF92C722)),
        shape2 = listOf(Color(0xFFBCEB4E), Color(0xFFAAE039), Color(0xFF90CC29), Color(0xFF76AF21)),
        shape1 = listOf(Color(0xFF71B55F), Color(0xFF5B9F51), Color(0xFF468B46), Color(0xFF326F39)),
    )

    val BabyPinkPurple = PixelMusicIconPalette(
        shape4 = listOf(Color(0xFFFFDDF1), Color(0xFFF9C9E7), Color(0xFFF1B3DD), Color(0xFFE69BD2)),
        shape3 = listOf(Color(0xFFEBCBFF), Color(0xFFDDB7FA), Color(0xFFCCA0F1), Color(0xFFBA89E5)),
        shape2 = listOf(Color(0xFFE8B9F5), Color(0xFFD8A1EE), Color(0xFFC487E4), Color(0xFFAD6DD3)),
        shape1 = listOf(Color(0xFFC47BD0), Color(0xFFB267C2), Color(0xFF984FAF), Color(0xFF7C3F94)),
    )

    val LimeLemonYellow = PixelMusicIconPalette(
        shape4 = listOf(Color(0xFFF0FF8A), Color(0xFFE6FA5C), Color(0xFFD4EF3F), Color(0xFFB9DB2C)),
        shape3 = listOf(Color(0xFFFFF27A), Color(0xFFFFE95A), Color(0xFFFFDD38), Color(0xFFEBC62A)),
        shape2 = listOf(Color(0xFFD8F85A), Color(0xFFC8EE3E), Color(0xFFB4DF29), Color(0xFF9AC51F)),
        shape1 = listOf(Color(0xFFA8D94C), Color(0xFF92C83B), Color(0xFF78B52E), Color(0xFF5D9528)),
    )
}
