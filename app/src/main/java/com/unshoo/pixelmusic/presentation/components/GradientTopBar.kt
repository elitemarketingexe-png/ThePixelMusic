package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.ui.theme.PixelMusicStatusBarStyle
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreGradientTopBar(
    title: String,
    startColor: Color,
    endColor: Color,
    contentColor: Color,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationIconClick: () -> Unit,
) {
    val gradientBrush = remember(startColor, endColor) {
        Brush.verticalGradient(colors = listOf(startColor, endColor))
    }

    PixelMusicStatusBarStyle(color = startColor)

    LargeTopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = title,
                color = contentColor,
                fontFamily = GoogleSansRounded
            )
        },
        expandedHeight = 160.dp,
        modifier = Modifier.background(brush = gradientBrush),
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(start = 10.dp),
                onClick = onNavigationIconClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = contentColor
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.auth_cd_back),
                    tint = startColor
                )
            }
        },
        colors = topAppBarColors(
            containerColor = Color.Transparent, // Background is handled by the gradient brush
            scrolledContainerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer, // Or a color that contrasts well with your typical gradient
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer // Same as title
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeGradientTopBar(
    onNavigationIconClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    onBetaClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    isScrolled: Boolean = false,
    floatingHeaderEnabled: Boolean = false
) {
    val baseContainerColor = MaterialTheme.colorScheme.primaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface
    val solidTintedColor = remember(baseContainerColor, surfaceColor) {
        androidx.compose.ui.graphics.Color(
            red = (baseContainerColor.red * 0.45f) + (surfaceColor.red * 0.55f),
            green = (baseContainerColor.green * 0.45f) + (surfaceColor.green * 0.55f),
            blue = (baseContainerColor.blue * 0.45f) + (surfaceColor.blue * 0.55f),
            alpha = 1f
        )
    }

    if (floatingHeaderEnabled) {
        val pillElevation by animateDpAsState(
            targetValue = if (isScrolled) 1.5.dp else 0.dp,
            label = "pill_elevation_anim"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = solidTintedColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = pillElevation
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.pixelmusic_base_monochrome),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.home_header_pill_title),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    shadowElevation = pillElevation
                ) {
                    FilledIconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = solidTintedColor,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = onTelegramClick,
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = stringResource(R.string.presentation_batch_g_topbar_cd_telegram),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    shadowElevation = pillElevation
                ) {
                    FilledIconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = solidTintedColor,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = onNavigationIconClick,
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_settings_24),
                            contentDescription = stringResource(R.string.settings_top_bar_title),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            color = solidTintedColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            TopAppBar(
                modifier = Modifier
                    .background(Color.Transparent)
                    .padding(bottom = 2.dp),
                title = { /* nada, usamos solo acciones */ },
                navigationIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.pixelmusic_base_monochrome),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = stringResource(R.string.home_header_pill_title),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 14.dp)
                    ) {
                        FilledIconButton(
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            onClick = onTelegramClick
                        ) {
                            Icon(
                                 imageVector = Icons.Rounded.Cloud,
                                 contentDescription = stringResource(R.string.presentation_batch_g_topbar_cd_telegram)
                            )
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        FilledIconButton(
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            onClick = onNavigationIconClick
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_settings_24),
                                contentDescription = stringResource(R.string.settings_top_bar_title)
                            )
                        }
                    }
                },
                colors = topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    }
}

