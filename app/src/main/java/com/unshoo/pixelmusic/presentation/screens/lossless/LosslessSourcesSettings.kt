/*
 * Settings → Lossless Sources.
 *
 * The settings surface for the ArchiveTune source-pool port: master switch, Source Pool key/URL
 * (runtime override of the BuildConfig values), source order, and per-provider sections for
 * Tidal / Qobuz / Deezer / Apple Music (enable, quality, sign-in, manual paste, health check).
 * Rendered inside SettingsCategoryScreen's LazyColumn item, so it is a plain Column of cards.
 */

package com.unshoo.pixelmusic.presentation.screens.lossless

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.unshoo.pixelmusic.presentation.utils.LocalAppHapticsConfig
import com.unshoo.pixelmusic.presentation.utils.performAppCompatHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.unshoo.pixelmusic.BuildConfig
import com.unshoo.pixelmusic.data.lossless.SourceCheckResult
import com.unshoo.pixelmusic.data.lossless.SourceCheckStatus
import com.unshoo.pixelmusic.data.lossless.constants.AppleMusicQuality
import com.unshoo.pixelmusic.data.lossless.constants.AudioSourceType
import com.unshoo.pixelmusic.data.lossless.constants.DeezerAudioQuality
import com.unshoo.pixelmusic.data.lossless.constants.QobuzAudioQuality
import com.unshoo.pixelmusic.data.lossless.constants.TidalAudioQuality
import com.unshoo.pixelmusic.data.lossless.constants.TidalSubscriptionStatus
import com.unshoo.pixelmusic.data.lossless.pool.PoolAccountManager
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.screens.ActionSettingsItem
import com.unshoo.pixelmusic.presentation.screens.AiApiKeyItem
import com.unshoo.pixelmusic.presentation.screens.SettingsItem
import com.unshoo.pixelmusic.presentation.screens.SwitchSettingItem
import com.unshoo.pixelmusic.presentation.screens.ThemeSelectorItem
import com.unshoo.pixelmusic.presentation.screens.getSettingsCardBorder
import com.unshoo.pixelmusic.presentation.viewmodel.LosslessSourcesViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PoolCounts

@Composable
fun LosslessSourcesSettings(
    navController: NavController,
    viewModel: LosslessSourcesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val poolCounts by viewModel.poolCounts.collectAsStateWithLifecycle()
    val poolReport by viewModel.poolStatusReport.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val checkResults by viewModel.checkResults.collectAsStateWithLifecycle()
    val poolCheck by viewModel.poolCheckResult.collectAsStateWithLifecycle()
    val message by viewModel.lastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    var resultDialog by remember { mutableStateOf<Pair<String, SourceCheckResult>?>(null) }
    var showOrderDialog by remember { mutableStateOf(false) }
    var showTidalInstancesDialog by remember { mutableStateOf(false) }
    var showQobuzInstancesDialog by remember { mutableStateOf(false) }
    var showQobuzTokensDialog by remember { mutableStateOf(false) }
    var showAppleTokensDialog by remember { mutableStateOf(false) }
    var showPoolUrlDialog by remember { mutableStateOf(false) }

    val secondary = MaterialTheme.colorScheme.secondary

    // ── Lossless streaming / Source Pool ──────────────────────────────────────────────────────
    LosslessSubsection(title = "Lossless streaming") {
        SwitchSettingItem(
            title = "Enable lossless sources",
            subtitle = "Resolve songs through Tidal, Qobuz, Deezer and Apple Music before falling back to " +
                "JioSaavn / YouTube. Streams are FLAC/ALAC where the account allows it.",
            checked = state.losslessEnabled,
            onCheckedChange = viewModel::setLosslessEnabled,
            leadingIcon = { Icon(Icons.Outlined.HighQuality, null, tint = secondary) },
        )
        SettingsItem(
            title = "Source order",
            subtitle = state.sourceOrder.joinToString(" → ") { it.displayName() },
            leadingIcon = { Icon(Icons.Outlined.SwapVert, null, tint = secondary) },
            onClick = { showOrderDialog = true },
        )
        ActionSettingsItem(
            title = "Clear resolved streams",
            subtitle = "Forget cached lossless URLs so the next play re-resolves through the source chain.",
            icon = { Icon(Icons.Outlined.Refresh, null, tint = secondary) },
            primaryActionLabel = "Clear",
            onPrimaryAction = viewModel::clearResolvedCache,
        )
    }

    LosslessSubsection(title = "Source Pool (ArchivePool)") {
        AiApiKeyItem(
            apiKey = state.poolApiKey,
            onApiKeySave = viewModel::savePoolApiKey,
            title = "Pool API key",
            subtitle =
                buildString {
                    append("Read key from the pool dashboard (atp_…), sent as Authorization: Bearer. ")
                    append(
                        if (state.poolBuildTimeKeyPresent) {
                            "A build-time key is baked in; a key entered here overrides it."
                        } else {
                            "No build-time key in this build — paste yours here (or set SOURCE_PROVIDER_KEY in local.properties)."
                        },
                    )
                },
        )
        SettingsItem(
            title = "Pool URL",
            subtitle =
                state.poolBaseUrl.ifBlank {
                    BuildConfig.SOURCE_PROVIDER_URL.ifBlank { "Not configured" } + " (default)"
                },
            leadingIcon = { Icon(Icons.Outlined.Link, null, tint = secondary) },
            onClick = { showPoolUrlDialog = true },
        )
        PoolHealthAndFeedCard(
            poolCounts = poolCounts,
            poolReport = poolReport,
            poolCheck = poolCheck,
            busy = busy,
            onCheckFeed = { viewModel.checkPool() },
            onFetchHealth = { viewModel.fetchPoolStatus() },
            onViewDetails = poolCheck?.let { r -> { resultDialog = "Source Pool" to r } },
        )
    }

    // ── Tidal ─────────────────────────────────────────────────────────────────────────────────
    LosslessSubsection(title = "Tidal") {
        SwitchSettingItem(
            title = "Use Tidal",
            subtitle = "Pool subscriber accounts stream directly from Tidal (FLAC / HiRes); public instances are the fallback.",
            checked = state.tidalEnabled,
            onCheckedChange = viewModel::setTidalEnabled,
            leadingIcon = { Icon(Icons.Outlined.GraphicEq, null, tint = secondary) },
        )
        ThemeSelectorItem(
            label = "Tidal quality",
            description = "HiRes needs a HiFi Plus account; FLAC is CD quality; AAC 320 is lossy.",
            options =
                mapOf(
                    TidalAudioQuality.HI_RES_LOSSLESS.name to "HiRes Lossless (up to 24-bit/192 kHz)",
                    TidalAudioQuality.FLAC.name to "Lossless FLAC (16-bit/44.1 kHz)",
                    TidalAudioQuality.AAC_320.name to "AAC 320 kbps",
                ),
            selectedKey = state.tidalQuality.name,
            onSelectionChanged = { key -> viewModel.setTidalQuality(TidalAudioQuality.valueOf(key)) },
            leadingIcon = { Icon(Icons.Outlined.HighQuality, null, tint = secondary) },
        )
        SwitchSettingItem(
            title = "Try account before public instances",
            subtitle = "When off, the account and pool tokens are skipped and only public instances are used (ArchiveTune behaviour).",
            checked = state.tidalAccountFirst,
            onCheckedChange = viewModel::setTidalAccountFirst,
            leadingIcon = { Icon(Icons.Outlined.AccountCircle, null, tint = secondary) },
            enabled = state.tidalEnabled,
        )
        if (state.tidalSignedIn) {
            ActionSettingsItem(
                title = "Signed in as ${state.tidalAccountName.ifBlank { "Tidal" }}",
                subtitle =
                    when (state.tidalSubscription) {
                        TidalSubscriptionStatus.PREMIUM -> "HiFi / Premium account — lossless available."
                        TidalSubscriptionStatus.FREE -> "Free account — Tidal may serve previews only."
                        TidalSubscriptionStatus.UNKNOWN -> "Subscription tier unknown."
                    } + if (state.tidalNeedsRelogin) " Session expired — please sign in again." else "",
                icon = { Icon(Icons.Outlined.AccountCircle, null, tint = secondary) },
                primaryActionLabel = if (state.tidalNeedsRelogin) "Sign in again" else "Sign out",
                onPrimaryAction = {
                    if (state.tidalNeedsRelogin) navController.navigateSafely(Screen.TidalLogin.route) else viewModel.signOutTidal()
                },
                secondaryActionLabel = if (state.tidalNeedsRelogin) "Sign out" else null,
                onSecondaryAction = if (state.tidalNeedsRelogin) ({ viewModel.signOutTidal() }) else null,
            )
        } else {
            SettingsItem(
                title = "Sign in with your Tidal account",
                subtitle = "Official PKCE login in a WebView, with web-player token capture as fallback. Optional — pool accounts work without it.",
                leadingIcon = { Icon(Icons.Outlined.Login, null, tint = secondary) },
                onClick = { navController.navigateSafely(Screen.TidalLogin.route) },
            )
        }
        SettingsItem(
            title = "Public instances",
            subtitle =
                buildString {
                    append("${state.tidalHealthyInstances} healthy in the last scan. ")
                    append(
                        if (state.tidalInstances.isBlank()) "Using pool discovery only — tap to add private instance URLs."
                        else "${state.tidalInstances.lines().count { it.isNotBlank() }} manual URL(s) configured.",
                    )
                },
            leadingIcon = { Icon(Icons.Outlined.Dns, null, tint = secondary) },
            onClick = { showTidalInstancesDialog = true },
        )
        CheckRow(
            source = AudioSourceType.TIDAL,
            result = checkResults[AudioSourceType.TIDAL],
            busy = AudioSourceType.TIDAL.name in busy || "tidal_instances" in busy,
            onCheck = { viewModel.checkSource(AudioSourceType.TIDAL) },
            onDetails = { r -> resultDialog = "Tidal" to r },
            secondaryLabel = "Rescan instances",
            onSecondary = { viewModel.refreshTidalInstances() },
        )
    }

    // ── Qobuz ─────────────────────────────────────────────────────────────────────────────────
    LosslessSubsection(title = "Qobuz") {
        SwitchSettingItem(
            title = "Use Qobuz",
            subtitle = "Pool Qobuz accounts and APIs resolve FLAC up to 24-bit/192 kHz.",
            checked = state.qobuzEnabled,
            onCheckedChange = viewModel::setQobuzEnabled,
            leadingIcon = { Icon(Icons.Outlined.GraphicEq, null, tint = secondary) },
        )
        ThemeSelectorItem(
            label = "Qobuz quality",
            description = "format_id 6 (CD), 7 (up to 24/96) or 27 (up to 24/192).",
            options =
                mapOf(
                    QobuzAudioQuality.MAX.name to "Max (up to 24-bit/192 kHz)",
                    QobuzAudioQuality.HI_RES.name to "Hi-Res (up to 24-bit/96 kHz)",
                    QobuzAudioQuality.FLAC.name to "Lossless FLAC (16-bit/44.1 kHz)",
                ),
            selectedKey = state.qobuzQuality.name,
            onSelectionChanged = { key -> viewModel.setQobuzQuality(QobuzAudioQuality.valueOf(key)) },
            leadingIcon = { Icon(Icons.Outlined.HighQuality, null, tint = secondary) },
        )
        SettingsItem(
            title = "Sign in with your Qobuz account",
            subtitle = "Captures the user token, app id and app secret from play.qobuz.com. Optional — pool accounts work without it.",
            leadingIcon = { Icon(Icons.Outlined.Login, null, tint = secondary) },
            onClick = { navController.navigateSafely(Screen.QobuzLogin.route) },
        )
        SettingsItem(
            title = "Manual tokens",
            subtitle =
                if (state.qobuzTokens.isEmpty()) "None added. Tap to paste token / app_id / app_secret."
                else state.qobuzTokens.joinToString { it.label.ifBlank { it.token.take(8) + "…" } },
            leadingIcon = { Icon(Icons.Outlined.Key, null, tint = secondary) },
            onClick = { showQobuzTokensDialog = true },
        )
        SettingsItem(
            title = "Qobuz API instances",
            subtitle =
                if (state.qobuzInstances.isBlank()) "Using pool discovery only — tap to add private instance URLs."
                else "${state.qobuzInstances.lines().count { it.isNotBlank() }} manual URL(s) configured.",
            leadingIcon = { Icon(Icons.Outlined.Dns, null, tint = secondary) },
            onClick = { showQobuzInstancesDialog = true },
        )
        CheckRow(
            source = AudioSourceType.QOBUZ,
            result = checkResults[AudioSourceType.QOBUZ],
            busy = AudioSourceType.QOBUZ.name in busy,
            onCheck = { viewModel.checkSource(AudioSourceType.QOBUZ) },
            onDetails = { r -> resultDialog = "Qobuz" to r },
        )
    }

    // ── Deezer ────────────────────────────────────────────────────────────────────────────────
    LosslessSubsection(title = "Deezer") {
        SwitchSettingItem(
            title = "Use Deezer",
            subtitle = "Pool Deezer accounts (ARL) stream FLAC / 320 kbps MP3; tracks are decrypted on device.",
            checked = state.deezerEnabled,
            onCheckedChange = viewModel::setDeezerEnabled,
            leadingIcon = { Icon(Icons.Outlined.GraphicEq, null, tint = secondary) },
        )
        ThemeSelectorItem(
            label = "Deezer quality",
            description = "FLAC needs a HiFi account; MP3 320 works with Premium; MP3 128 with any account.",
            options =
                mapOf(
                    DeezerAudioQuality.FLAC.name to "Lossless FLAC (16-bit/44.1 kHz)",
                    DeezerAudioQuality.MP3_320.name to "MP3 320 kbps",
                    DeezerAudioQuality.MP3_128.name to "MP3 128 kbps",
                ),
            selectedKey = state.deezerQuality.name,
            onSelectionChanged = { key -> viewModel.setDeezerQuality(DeezerAudioQuality.valueOf(key)) },
            leadingIcon = { Icon(Icons.Outlined.HighQuality, null, tint = secondary) },
        )
        if (state.deezerSignedIn) {
            ActionSettingsItem(
                title = "Signed in as ${state.deezerAccountName.ifBlank { "Deezer" }}",
                subtitle = if (state.deezerAccountPremium) "HiFi account — FLAC available." else "No lossless entitlement — 320 kbps MP3 at best.",
                icon = { Icon(Icons.Outlined.AccountCircle, null, tint = secondary) },
                primaryActionLabel = "Sign out",
                onPrimaryAction = viewModel::signOutDeezer,
            )
        } else {
            SettingsItem(
                title = "Sign in with your Deezer account",
                subtitle = "WebView login or paste your arl cookie. Optional — pool accounts work without it.",
                leadingIcon = { Icon(Icons.Outlined.Login, null, tint = secondary) },
                onClick = { navController.navigateSafely(Screen.DeezerLogin.route) },
            )
        }
        CheckRow(
            source = AudioSourceType.DEEZER,
            result = checkResults[AudioSourceType.DEEZER],
            busy = AudioSourceType.DEEZER.name in busy,
            onCheck = { viewModel.checkSource(AudioSourceType.DEEZER) },
            onDetails = { r -> resultDialog = "Deezer" to r },
        )
    }

    // ── Apple Music ───────────────────────────────────────────────────────────────────────────
    LosslessSubsection(title = "Apple Music") {
        SwitchSettingItem(
            title = "Use Apple Music",
            subtitle = "Needs a Media-User-Token (yours or a pool account) plus a developer token (auto-scraped). " +
                "Lossless ALAC streams play through Widevine L3.",
            checked = state.appleEnabled,
            onCheckedChange = viewModel::setAppleEnabled,
            leadingIcon = { Icon(Icons.Outlined.GraphicEq, null, tint = secondary) },
        )
        ThemeSelectorItem(
            label = "Apple Music quality",
            description = "Hi-Res needs a subscription with Hi-Res Lossless enabled; AAC is 256 kbps.",
            options =
                mapOf(
                    AppleMusicQuality.HI_RES_LOSSLESS.name to "Hi-Res Lossless (up to 24-bit/192 kHz)",
                    AppleMusicQuality.LOSSLESS.name to "Lossless ALAC (up to 24-bit/48 kHz)",
                    AppleMusicQuality.AAC.name to "AAC 256 kbps",
                ),
            selectedKey = state.appleQuality.name,
            onSelectionChanged = { key -> viewModel.setAppleQuality(AppleMusicQuality.valueOf(key)) },
            leadingIcon = { Icon(Icons.Outlined.HighQuality, null, tint = secondary) },
        )
        if (state.appleMediaUserToken.isNotBlank()) {
            ActionSettingsItem(
                title = "Signed in to Apple Music",
                subtitle =
                    if (state.appleDevToken.isNotBlank()) "Media-User-Token and developer token stored."
                    else "Media-User-Token stored; developer token is auto-scraped from the web player.",
                icon = { Icon(Icons.Outlined.AccountCircle, null, tint = secondary) },
                primaryActionLabel = "Sign out",
                onPrimaryAction = viewModel::signOutApple,
                secondaryActionLabel = "Edit tokens",
                onSecondaryAction = { showAppleTokensDialog = true },
            )
        } else {
            SettingsItem(
                title = "Sign in with Apple Music (web)",
                subtitle = "Sign in with your Apple ID; the Music User Token is captured and verified automatically.",
                leadingIcon = { Icon(Icons.Outlined.Login, null, tint = secondary) },
                onClick = { navController.navigateSafely(Screen.AppleMusicLogin.route) },
            )
            SettingsItem(
                title = "Paste tokens manually",
                subtitle = "Media-User-Token (required) and developer token (optional).",
                leadingIcon = { Icon(Icons.Outlined.Key, null, tint = secondary) },
                onClick = { showAppleTokensDialog = true },
            )
        }
        CheckRow(
            source = AudioSourceType.APPLE,
            result = checkResults[AudioSourceType.APPLE],
            busy = AudioSourceType.APPLE.name in busy,
            onCheck = { viewModel.checkSource(AudioSourceType.APPLE) },
            onDetails = { r -> resultDialog = "Apple Music" to r },
        )
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────────────────────
    resultDialog?.let { (title, result) ->
        AlertDialog(
            onDismissRequest = { resultDialog = null },
            title = { Text("$title — ${result.status.label()}") },
            text = {
                Text(
                    result.summary,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { resultDialog = null }) { Text("Close") } },
        )
    }

    if (showOrderDialog) {
        SourceOrderDialog(
            order = state.sourceOrder,
            onDismiss = { showOrderDialog = false },
            onSave = { viewModel.setSourceOrder(it); showOrderDialog = false },
        )
    }

    if (showPoolUrlDialog) {
        MultilineInputDialog(
            title = "Pool URL",
            hint = "https://archivepool.vercel.app (leave empty to use the build-time default)",
            initial = state.poolBaseUrl,
            singleLine = true,
            onDismiss = { showPoolUrlDialog = false },
            onSave = { viewModel.savePoolBaseUrl(it); showPoolUrlDialog = false },
        )
    }

    if (showTidalInstancesDialog) {
        MultilineInputDialog(
            title = "Tidal instances",
            hint = "One base URL per line (or comma-separated). Pool discovery is merged in automatically.",
            initial = state.tidalInstances,
            onDismiss = { showTidalInstancesDialog = false },
            onSave = { viewModel.setTidalInstances(it); showTidalInstancesDialog = false },
        )
    }

    if (showQobuzInstancesDialog) {
        MultilineInputDialog(
            title = "Qobuz API instances",
            hint = "One base URL per line (or comma-separated). Pool discovery is merged in automatically.",
            initial = state.qobuzInstances,
            onDismiss = { showQobuzInstancesDialog = false },
            onSave = { viewModel.setQobuzInstances(it); showQobuzInstancesDialog = false },
        )
    }

    if (showQobuzTokensDialog) {
        QobuzTokensDialog(
            tokens = state.qobuzTokens,
            onDismiss = { showQobuzTokensDialog = false },
            onAdd = { viewModel.addQobuzTokens(it) },
            onRemove = { viewModel.removeQobuzToken(it) },
        )
    }

    if (showAppleTokensDialog) {
        AppleTokensDialog(
            mediaToken = state.appleMediaUserToken,
            devToken = state.appleDevToken,
            busy = AudioSourceType.APPLE.name in busy,
            onDismiss = { showAppleTokensDialog = false },
            onSave = { media, dev -> viewModel.saveAppleTokens(media, dev); showAppleTokensDialog = false },
        )
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun CheckRow(
    source: AudioSourceType,
    result: SourceCheckResult?,
    busy: Boolean,
    onCheck: () -> Unit,
    onDetails: (SourceCheckResult) -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val secondary = MaterialTheme.colorScheme.secondary
    ActionSettingsItem(
        title = "Check ${source.displayName()} source",
        subtitle =
            result?.let { "${it.status.label()} — ${it.summary.lineSequence().first()}" }
                ?: "Probe pool credentials, instances and the provider API.",
        icon = {
            if (busy) CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.HealthAndSafety, null, tint = secondary)
        },
        primaryActionLabel = if (result == null) "Check now" else "Re-check",
        onPrimaryAction = onCheck,
        secondaryActionLabel = result?.let { "Details" } ?: secondaryLabel,
        onSecondaryAction = result?.let { r -> { onDetails(r) } } ?: onSecondary,
        enabled = !busy,
    )
    if (result != null && secondaryLabel != null && onSecondary != null) {
        ActionSettingsItem(
            title = secondaryLabel,
            subtitle = "Probe every configured / discovered instance again.",
            icon = { Icon(Icons.Outlined.Refresh, null, tint = secondary) },
            primaryActionLabel = "Run",
            onPrimaryAction = onSecondary,
            enabled = !busy,
        )
    }
}

@Composable
private fun LosslessSubsection(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
    )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Transparent),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun MultilineInputDialog(
    title: String,
    hint: String,
    initial: String,
    singleLine: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SourceOrderDialog(
    order: List<AudioSourceType>,
    onDismiss: () -> Unit,
    onSave: (List<AudioSourceType>) -> Unit,
) {
    var working by remember(order) { mutableStateOf(order) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Source order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Sources are tried top to bottom; the first one that resolves the song wins. " +
                        "JioSaavn and YouTube are PixelMusic's built-in fallbacks after this chain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                working.forEachIndexed { index, source ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = getSettingsCardBorder(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}. ${source.displayName()}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    working = working.toMutableList().also { it.add(index - 1, it.removeAt(index)) }
                                },
                            ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up") }
                            IconButton(
                                enabled = index < working.lastIndex,
                                onClick = {
                                    working = working.toMutableList().also { it.add(index + 1, it.removeAt(index)) }
                                },
                            ) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(working) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun QobuzTokensDialog(
    tokens: List<com.unshoo.pixelmusic.data.lossless.qobuz.QobuzToken>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (com.unshoo.pixelmusic.data.lossless.qobuz.QobuzToken) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Qobuz tokens") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                tokens.forEach { token ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(token.label.ifBlank { "Token" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "${token.token.take(10)}… · app ${token.appId}" +
                                    (token.subscription.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRemove(token) }) { Text("Remove") }
                    }
                }
                Text(
                    "Paste a token, a `token:app_id:app_secret` triple, `key=value` lines, or the JSON a token source " +
                        "exports — several at once are fine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("user_auth_token / app_id / app_secret") },
                )
                FilledTonalButton(
                    onClick = { onAdd(input); input = "" },
                    enabled = input.isNotBlank(),
                ) { Text("Add") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun AppleTokensDialog(
    mediaToken: String,
    devToken: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var media by remember(mediaToken) { mutableStateOf(mediaToken) }
    var dev by remember(devToken) { mutableStateOf(devToken) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apple Music tokens") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = media,
                    onValueChange = { media = it },
                    label = { Text("Media-User-Token") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dev,
                    onValueChange = { dev = it },
                    label = { Text("Developer token (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "The pair is verified against /v1/me/storefront before it is saved. Leave the developer token empty " +
                        "to use the web player's token, which PixelMusic scrapes and refreshes automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onSave(media, dev) }) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Verify & save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PoolHealthAndFeedCard(
    poolCounts: PoolCounts,
    poolReport: PoolAccountManager.PoolStatusReport?,
    poolCheck: SourceCheckResult?,
    busy: Set<String>,
    onCheckFeed: () -> Unit,
    onFetchHealth: () -> Unit,
    onViewDetails: (() -> Unit)?,
) {
    val isBusy = "pool" in busy || "pool_status" in busy
    var isExpanded by remember { mutableStateOf(false) }
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "arrowRotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = getSettingsCardBorder()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header: Title + live indicator + expand chevron
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        performAppCompatHapticFeedback(
                            view,
                            appHapticsConfig,
                            HapticFeedbackConstantsCompat.GESTURE_START
                        )
                        isExpanded = !isExpanded
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HealthAndSafety,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Live Pool Health & Feed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val summaryText = if (poolCounts.total > 0) {
                            "${poolCounts.total} cached accounts"
                        } else {
                            "0 cached accounts"
                        }
                        Text(
                            text = if (isExpanded) "$summaryText in feed" else "$summaryText · Tap to expand",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(arrowRotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // Clean service breakdown list (minimal & concise)
                    val services = listOf(
                        Triple("Tidal", "tidal", poolCounts.tidal),
                        Triple("Qobuz", "qobuz", poolCounts.qobuz),
                        Triple("Deezer", "deezer", poolCounts.deezer),
                        Triple("Apple Music", "apple", poolCounts.apple),
                        Triple("Amazon", "amazon", poolCounts.amazon)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        services.forEach { (displayName, serviceKey, cachedCount) ->
                            val categories = poolReport?.categories?.filter {
                                it.service.equals(serviceKey, ignoreCase = true)
                            }.orEmpty()
                            val alive = categories.sumOf { it.alive }
                            val premium = categories.sumOf { it.premium }
                            val dead = categories.sumOf { it.dead }
                            val pending = categories.sumOf { it.pending }

                            val statusDotColor = when {
                                alive > 0 -> Color(0xFF4CAF50)
                                pending > 0 -> Color(0xFFFFA000)
                                dead > 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(92.dp)
                                )
                                Text(
                                    text = "$cachedCount cached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (serviceKey == "amazon") {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "· (not playable)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                } else if (poolReport != null) {
                                    Spacer(Modifier.width(6.dp))
                                    val parts = buildList {
                                        if (alive > 0) add("$alive alive" + if (premium > 0) " ($premium prem)" else "")
                                        if (pending > 0) add("$pending pend")
                                        if (dead > 0) add("$dead dead")
                                        if (alive == 0 && pending == 0 && dead == 0) add("offline")
                                    }
                                    Text(
                                        text = "· " + parts.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (alive > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (poolCheck != null) {
                        Text(
                            text = "${poolCheck.status.label()}: ${poolCheck.summary.lineSequence().first()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Compact Expressive Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onCheckFeed,
                            enabled = "pool" !in busy,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Cloud, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Check feed", style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = onFetchHealth,
                            enabled = "pool_status" !in busy,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh health", style = MaterialTheme.typography.labelMedium)
                        }

                        if (onViewDetails != null) {
                            OutlinedButton(
                                onClick = onViewDetails,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Details", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun AudioSourceType.displayName(): String =
    when (this) {
        AudioSourceType.TIDAL -> "Tidal"
        AudioSourceType.QOBUZ -> "Qobuz"
        AudioSourceType.QOBUZ_BACKUP -> "Qobuz backup"
        AudioSourceType.DEEZER -> "Deezer"
        AudioSourceType.APPLE -> "Apple Music"
        AudioSourceType.AMAZON -> "Amazon Music"
        AudioSourceType.JIOSAAVN -> "JioSaavn"
        AudioSourceType.YOUTUBE -> "YouTube"
    }

private fun SourceCheckStatus.label(): String =
    when (this) {
        SourceCheckStatus.READY -> "Ready"
        SourceCheckStatus.DEGRADED -> "Degraded"
        SourceCheckStatus.NOT_CONFIGURED -> "Not configured"
        SourceCheckStatus.UNSUPPORTED -> "Unsupported"
        SourceCheckStatus.UNREACHABLE -> "Unreachable"
    }
