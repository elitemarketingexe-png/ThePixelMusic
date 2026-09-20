/*
 * Ported from ArchiveTune (2026) — © Rukamori, GPL-3.0.
 * Origin: playback/MusicService.kt (resolveMultiSourceDataSpec, sourceResolutionChain,
 * resolveTidalStream / ensureValidTidalToken / refreshTidalToken, resolveQobuzStream,
 * resolveDeezerStream, resolveAppleStream / appleStreamFile, applyDirectStream,
 * persistDirectStreamFormat, measuredBitrate, sourceCacheKey) and
 * playback/LosslessStreamResolver.kt.
 *
 * PixelMusic entry point for the ArchiveTune multi-source ("lossless") chain. It is called from
 * YoutubeHelper.getSongPlayerUrl* before the JioSaavn/YouTube paths and returns a playable URI
 * plus the stream's format description, or null when no source cleared the metadata-match gate.
 *
 * Resolution order (per user preference, see AudioSourceConfig.DEFAULT_ORDER):
 *   TIDAL  → personal account token (auto-refresh, 401 → force refresh + retry)
 *           → Source Pool Tidal accounts (401 → report "dead", cooldown on failure)
 *           → public Tidal instances (user list ∪ pool discovery, health-ranked)
 *   QOBUZ  → user tokens (JSON) ∪ pool Qobuz accounts, user instances ∪ pool discovery
 *   DEEZER → manual ARL ∪ pool Deezer accounts (Blowfish-decrypted through deezer:// scheme)
 *   APPLE  → Media-User-Token (pasted or pool) + developer token; Widevine L3 via AppleMusicDrm
 *   AMAZON → no resolver (CENC, no decryption step — same as ArchiveTune)
 *   JIOSAAVN / YOUTUBE → handled by PixelMusic's existing paths after this resolver returns null
 */

package com.unshoo.pixelmusic.data.lossless

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import com.unshoo.pixelmusic.data.lossless.applemusic.AppleMusicAudioProvider
import com.unshoo.pixelmusic.data.lossless.applemusic.AppleMusicDrm
import com.unshoo.pixelmusic.data.lossless.applemusic.AppleMusicVirtualStream
import com.unshoo.pixelmusic.data.lossless.applemusic.AppleTrackDrmInfo
import com.unshoo.pixelmusic.data.lossless.audiosource.AudioSourceConfig
import com.unshoo.pixelmusic.data.lossless.audiosource.DirectStream
import com.unshoo.pixelmusic.data.lossless.audiosource.SongSourceOverride
import com.unshoo.pixelmusic.data.lossless.audiosource.TitleMatch
import com.unshoo.pixelmusic.data.lossless.audiosource.pcmBitrateOrNull
import com.unshoo.pixelmusic.data.lossless.constants.AppleMusicQuality
import com.unshoo.pixelmusic.data.lossless.constants.AppleMusicQualityKey
import com.unshoo.pixelmusic.data.lossless.constants.AppleMusicMediaUserTokenKey
import com.unshoo.pixelmusic.data.lossless.constants.AppleMusicSourceEnabledKey
import com.unshoo.pixelmusic.data.lossless.constants.AudioSourceOrderKey
import com.unshoo.pixelmusic.data.lossless.constants.AudioSourceType
import com.unshoo.pixelmusic.data.lossless.constants.DeezerArlKey
import com.unshoo.pixelmusic.data.lossless.constants.PoolApiKeyKey
import com.unshoo.pixelmusic.data.lossless.constants.DeezerAudioQuality
import com.unshoo.pixelmusic.data.lossless.constants.DeezerAudioQualityKey
import com.unshoo.pixelmusic.data.lossless.constants.DeezerEnabledKey
import com.unshoo.pixelmusic.data.lossless.constants.LosslessStreamingEnabledKey
import com.unshoo.pixelmusic.data.lossless.constants.QobuzAudioQuality
import com.unshoo.pixelmusic.data.lossless.constants.QobuzAudioQualityKey
import com.unshoo.pixelmusic.data.lossless.constants.QobuzEnabledKey
import com.unshoo.pixelmusic.data.lossless.constants.QobuzInstancesKey
import com.unshoo.pixelmusic.data.lossless.constants.QobuzLastProbeTrackKey
import com.unshoo.pixelmusic.data.lossless.constants.QobuzTokensKey
import com.unshoo.pixelmusic.data.lossless.constants.SongSourceOverrideKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalAccessTokenKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalAccountFirstKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalAudioQuality
import com.unshoo.pixelmusic.data.lossless.constants.TidalAudioQualityKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalAuthFlowKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalCountryCodeKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalEnabledKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalInstancesKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalLastProbeTrackKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalNeedsReloginKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalRefreshTokenKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalTokenExpiryKey
import com.unshoo.pixelmusic.data.lossless.constants.TidalUserIdKey
import com.unshoo.pixelmusic.data.lossless.constants.getAsync
import com.unshoo.pixelmusic.data.lossless.constants.toFormatId
import com.unshoo.pixelmusic.data.lossless.constants.toFormatName
import com.unshoo.pixelmusic.data.lossless.deezer.DeezerAudioProvider
import com.unshoo.pixelmusic.data.lossless.playback.LosslessSchemeRoutingDataSource
import com.unshoo.pixelmusic.data.lossless.pool.PoolAccountManager
import com.unshoo.pixelmusic.data.lossless.qobuz.QobuzAudioProvider
import com.unshoo.pixelmusic.data.lossless.qobuz.QobuzToken
import com.unshoo.pixelmusic.data.lossless.tidal.TidalAccountManager
import com.unshoo.pixelmusic.data.lossless.tidal.TidalAudioProvider
import com.unshoo.pixelmusic.data.lossless.tidal.TidalInstanceHealthManager
import com.unshoo.pixelmusic.data.preferences.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object LosslessStreamResolver {
    private const val TAG = "LosslessResolver"

    /** Same TTL ArchiveTune uses for its `directStreamCache` (DIRECT_STREAM_CACHE_TTL_MS). */
    private const val DIRECT_STREAM_CACHE_TTL_MS = 5 * 60 * 1000L

    /** How long a resolved URI is considered playable by the player-side freshness checks. */
    private const val URI_FRESHNESS_MS = 60 * 60 * 1000L

    private const val APPLE_CACHE_LIMIT_BYTES = 300L * 1024 * 1024

    /** What the player receives: a URI plus enough format info for caches, badges and ExoPlayer. */
    data class Result(
        val mediaId: String,
        val uri: String,
        /** Bare mime type, e.g. `audio/flac`, `audio/mp4`. */
        val mimeType: String,
        val codecs: String,
        /** Bits per second (measured from size/duration when possible, otherwise a label heuristic). */
        val bitrate: Int,
        val sampleRate: Int?,
        val bitDepth: Int?,
        val contentLength: Long?,
        /** Human readable label, e.g. "Tidal HI_RES_LOSSLESS", "Qobuz FLAC 24/96", "Apple Music ctrp256". */
        val label: String,
        val source: AudioSourceType,
        val resolvedAtMs: Long = System.currentTimeMillis(),
    ) {
        val isLossless: Boolean
            get() =
                codecs.contains("flac", ignoreCase = true) ||
                    codecs.contains("alac", ignoreCase = true) ||
                    mimeType.contains("flac", ignoreCase = true) ||
                    label.uppercase(Locale.US).let { it.contains("LOSSLESS") || it.contains("FLAC") || it.contains("HI_RES") }

        val isHiRes: Boolean
            get() = (sampleRate ?: 0) > 48_000 || label.uppercase(Locale.US).let { it.contains("HI_RES") || it.contains("HIRES") }
    }

    /** Everything the chain needs to know about the wanted recording. */
    data class Request(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
        val isrc: String? = null,
    )

    private class CachedDirectStream(
        val stream: DirectStream,
        val expiresAtMs: Long,
    )

    private val directStreamCache = ConcurrentHashMap<String, CachedDirectStream>()

    /** resolved URI → time after which the player must re-resolve. */
    private val uriFreshness = ConcurrentHashMap<String, Long>()

    /** mediaId → last accepted result (drives the Now Playing quality badge). */
    private val resolvedByMediaId = ConcurrentHashMap<String, Result>()

    private val _lastResolved = MutableStateFlow<Result?>(null)

    /** Emits every accepted lossless resolution (latest wins); UI observes this for the badge. */
    val lastResolved: StateFlow<Result?> = _lastResolved.asStateFlow()

    /** mediaId → sources that resolved *and* cleared the match gate at least once this session. */
    private val resolvedSourcesByMediaId = ConcurrentHashMap<String, MutableSet<AudioSourceType>>()

    private val inFlight = ConcurrentHashMap<String, Mutex>()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tidalTokenRefreshLock = Any()

    /** Shared client for Apple playlist/fMP4 downloads and HEAD length back-fill. */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // ── Public helpers used by the player / YoutubeHelper ─────────────────────────────────────

    /** Master switch; defaults to on only when the build ships a pool key (see LosslessPreferenceKeys). */
    suspend fun isEnabled(context: Context): Boolean =
        context.dataStore.getAsync(LosslessStreamingEnabledKey) ?: defaultEnabled()

    fun isEnabledBlocking(context: Context): Boolean =
        runCatching { runBlocking(Dispatchers.IO) { isEnabled(context) } }.getOrDefault(false)

    /** Default for the master switch when the user never touched it: on iff a pool read key exists (build-time or runtime). */
    fun defaultEnabled(): Boolean =
        com.unshoo.pixelmusic.BuildConfig.SOURCE_PROVIDER_KEY.isNotBlank() || PoolAccountManager.hasReadKey

    /**
     * Emits true when lossless streaming is enabled AND at least one lossless API key
     * or service account (Tidal, Deezer, Apple Music, Qobuz, or Source Pool) is configured/signed in.
     */
    fun isLosslessConfiguredOrSignedInFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { p ->
            val masterEnabled = p[LosslessStreamingEnabledKey] ?: defaultEnabled()
            val hasBuildKey = com.unshoo.pixelmusic.BuildConfig.SOURCE_PROVIDER_KEY.isNotBlank()
            val hasPoolKey = !p[PoolApiKeyKey].isNullOrBlank() || PoolAccountManager.hasReadKey || PoolAccountManager.hasAccounts()
            val hasTidal = !p[TidalAccessTokenKey].isNullOrBlank()
            val hasDeezer = !p[DeezerArlKey].isNullOrBlank()
            val hasApple = !p[AppleMusicMediaUserTokenKey].isNullOrBlank()
            val hasQobuz = !p[QobuzTokensKey].isNullOrBlank()

            masterEnabled && (hasBuildKey || hasPoolKey || hasTidal || hasDeezer || hasApple || hasQobuz)
        }

    /** True when [uri] came out of this resolver (any source, any scheme). */
    fun isLosslessUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        if (uriFreshness.containsKey(uri)) return true
        return LosslessSchemeRoutingDataSource.isCustomLosslessScheme(uri) ||
            uri.startsWith("apple-pending:") ||
            resolvedByMediaId.values.any { it.uri == uri }
    }

    /** Whether a URI produced by [resolve] may still be handed to the player without re-resolving. */
    fun isFreshUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val until = uriFreshness[uri] ?: return false
        if (until <= System.currentTimeMillis()) {
            uriFreshness.remove(uri)
            return false
        }
        // Apple flattened files live in cacheDir/applemusic; the LRU may have evicted them.
        if (uri.startsWith("file:")) {
            val exists = runCatching { File(Uri.parse(uri).path.orEmpty()).let { it.isFile && it.length() > 0L } }.getOrDefault(false)
            if (!exists) {
                uriFreshness.remove(uri)
                return false
            }
        }
        return true
    }

    fun resultFor(mediaId: String?): Result? = mediaId?.let { resolvedByMediaId[it] }

    fun availableSourcesForSong(mediaId: String): List<AudioSourceType> =
        resolvedSourcesByMediaId[mediaId]?.toList().orEmpty()

    /** Drop every cached decision for [mediaId] (used after a per-song source override changes). */
    fun clearResolved(mediaId: String) {
        AudioSourceType.entries.forEach { directStreamCache.remove(sourceCacheKey(it, mediaId)) }
        resolvedByMediaId.remove(mediaId)?.let { uriFreshness.remove(it.uri) }
        resolvedSourcesByMediaId.remove(mediaId)
        AppleMusicDrm.clear(mediaId)
    }

    fun clearAll() {
        directStreamCache.clear()
        resolvedByMediaId.clear()
        uriFreshness.clear()
        resolvedSourcesByMediaId.clear()
    }

    suspend fun setSongSourceOverride(
        context: Context,
        mediaId: String,
        source: AudioSourceType?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[SongSourceOverrideKey] = SongSourceOverride.withOverride(prefs[SongSourceOverrideKey], mediaId, source)
        }
        clearResolved(mediaId)
    }

    // ── Preference readers ────────────────────────────────────────────────────────────────────

    private suspend fun prefs(context: Context) = context.dataStore.data.first()

    private fun parseTidalAudioQuality(stored: String?): TidalAudioQuality =
        runCatching { TidalAudioQuality.valueOf(stored ?: TidalAudioQuality.FLAC.name) }.getOrDefault(TidalAudioQuality.FLAC)

    private fun parseAppleMusicQuality(stored: String?): AppleMusicQuality =
        runCatching { AppleMusicQuality.valueOf(stored ?: AppleMusicQuality.LOSSLESS.name) }.getOrDefault(AppleMusicQuality.LOSSLESS)

    private fun parseQobuzAudioQuality(stored: String?): QobuzAudioQuality =
        runCatching { QobuzAudioQuality.valueOf(stored ?: QobuzAudioQuality.FLAC.name) }.getOrDefault(QobuzAudioQuality.FLAC)

    private fun parseDeezerAudioQuality(stored: String?): DeezerAudioQuality =
        runCatching { DeezerAudioQuality.valueOf(stored ?: DeezerAudioQuality.FLAC.name) }.getOrDefault(DeezerAudioQuality.FLAC)

    private fun parseMultiline(raw: String?): List<String> =
        raw.orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private class SourceSettings(
        val tidalEnabled: Boolean,
        val qobuzEnabled: Boolean,
        val deezerEnabled: Boolean,
        val appleEnabled: Boolean,
        val rawOrder: String?,
        val overrideRaw: String?,
        val tidalQuality: TidalAudioQuality,
        val tidalAccountFirst: Boolean,
        val tidalInstances: List<String>,
        val qobuzQuality: QobuzAudioQuality,
        val qobuzInstances: List<String>,
        val qobuzTokensJson: String,
        val deezerQuality: DeezerAudioQuality,
        val appleQuality: AppleMusicQuality,
    )

    private suspend fun readSettings(context: Context): SourceSettings {
        val p = prefs(context)
        return SourceSettings(
            tidalEnabled = p[TidalEnabledKey] ?: true,
            qobuzEnabled = p[QobuzEnabledKey] ?: false,
            deezerEnabled = p[DeezerEnabledKey] ?: false,
            appleEnabled = p[AppleMusicSourceEnabledKey] ?: true,
            rawOrder = p[AudioSourceOrderKey]?.ifBlank { null },
            overrideRaw = p[SongSourceOverrideKey],
            tidalQuality = parseTidalAudioQuality(p[TidalAudioQualityKey]),
            tidalAccountFirst = p[TidalAccountFirstKey] ?: true,
            tidalInstances = parseMultiline(p[TidalInstancesKey]),
            qobuzQuality = parseQobuzAudioQuality(p[QobuzAudioQualityKey]),
            qobuzInstances = parseMultiline(p[QobuzInstancesKey]),
            qobuzTokensJson = p[QobuzTokensKey].orEmpty(),
            deezerQuality = parseDeezerAudioQuality(p[DeezerAudioQualityKey]),
            appleQuality = parseAppleMusicQuality(p[AppleMusicQualityKey]),
        )
    }

    /**
     * ArchiveTune: `sourceResolutionChain()` — user order filtered by the per-source toggles with
     * ArchiveTune's defaults (Tidal + Apple on, everything else off), cut before YOUTUBE.
     * QOBUZ_BACKUP, AMAZON and JIOSAAVN never have a resolver here (see file header).
     */
    private fun resolutionChain(s: SourceSettings): List<AudioSourceType> {
        val enabledDefaults =
            mapOf(
                AudioSourceType.TIDAL to s.tidalEnabled,
                AudioSourceType.QOBUZ to s.qobuzEnabled,
                AudioSourceType.QOBUZ_BACKUP to false,
                AudioSourceType.DEEZER to s.deezerEnabled,
                AudioSourceType.APPLE to s.appleEnabled,
                AudioSourceType.AMAZON to false,
                AudioSourceType.JIOSAAVN to false,
                AudioSourceType.YOUTUBE to true,
            )
        return AudioSourceConfig
            .resolutionChain(rawOrder = s.rawOrder, enabledSet = null, defaults = enabledDefaults)
            .takeWhile { it != AudioSourceType.YOUTUBE }
            .filter { it != AudioSourceType.QOBUZ_BACKUP && it != AudioSourceType.AMAZON && it != AudioSourceType.JIOSAAVN }
    }

    private fun isSourceEnabled(source: AudioSourceType, s: SourceSettings): Boolean =
        when (source) {
            AudioSourceType.YOUTUBE -> true
            AudioSourceType.TIDAL -> s.tidalEnabled
            AudioSourceType.QOBUZ -> s.qobuzEnabled
            AudioSourceType.DEEZER -> s.deezerEnabled
            AudioSourceType.APPLE -> s.appleEnabled
            AudioSourceType.QOBUZ_BACKUP, AudioSourceType.AMAZON, AudioSourceType.JIOSAAVN -> false
        }

    /** True when at least one lossless source is switched on (cheap pre-check for callers). */
    suspend fun anySourceEnabled(context: Context): Boolean {
        if (!isEnabled(context)) return false
        val s = readSettings(context)
        return resolutionChain(s).isNotEmpty()
    }

    // ── Main entry point ──────────────────────────────────────────────────────────────────────

    /**
     * Port of `resolveMultiSourceDataSpec`. Returns the winning stream or null when every source
     * either failed or was rejected by [TitleMatch]. Never throws.
     *
     * @param lowDataMode ArchiveTune skips the lossless chain entirely on low-data connections.
     */
    suspend fun resolve(
        context: Context,
        request: Request,
        lowDataMode: Boolean = false,
    ): Result? =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            if (!isEnabled(appContext)) return@withContext null
            val mediaId = request.mediaId
            if (mediaId.isBlank() || request.title.isBlank()) return@withContext null

            val mutex = inFlight.getOrPut(mediaId) { Mutex() }
            try {
                mutex.withLock {
                    runCatching { resolveLocked(appContext, request, lowDataMode) }
                        .onFailure { Timber.tag(TAG).w(it, "Lossless resolve crashed for %s", mediaId) }
                        .getOrNull()
                }
            } finally {
                inFlight.remove(mediaId, mutex)
            }
        }

    private suspend fun resolveLocked(
        context: Context,
        request: Request,
        lowDataMode: Boolean,
    ): Result? {
        val mediaId = request.mediaId
        val settings = readSettings(context)
        val now = System.currentTimeMillis()

        val override = SongSourceOverride.get(settings.overrideRaw, mediaId)

        // Cache probe (same order the chain would use), unless the connection is low-data.
        val probeOrder =
            when (override) {
                null -> resolutionChain(settings)
                AudioSourceType.YOUTUBE -> emptyList()
                else -> listOf(override)
            }
        for (source in probeOrder) {
            val cacheKey = sourceCacheKey(source, mediaId)
            val cached = directStreamCache[cacheKey] ?: continue
            if (cached.expiresAtMs <= now) {
                directStreamCache.remove(cacheKey, cached)
                continue
            }
            if (!lowDataMode && streamStillUsable(cached.stream)) {
                Timber.tag(TAG).d("Multi-source cache HIT for %s: %s [%s]", mediaId, source.name, cached.stream.label)
                recordResolvedSource(mediaId, source)
                return publish(mediaId, cached.stream, fromCache = true)
            }
        }

        val overrideStillEnabled =
            override == null || override == AudioSourceType.YOUTUBE || isSourceEnabled(override, settings)
        val chain =
            when (override) {
                null -> resolutionChain(settings)
                AudioSourceType.YOUTUBE -> {
                    Timber.tag(TAG).d("Per-song override: %s pinned to YouTube; skipping lossless", mediaId)
                    emptyList()
                }
                else ->
                    if (overrideStillEnabled) {
                        Timber.tag(TAG).d("Per-song override: %s pinned to %s", mediaId, override.name)
                        listOf(override)
                    } else {
                        Timber.tag(TAG).w(
                            "Per-song override: %s pinned to %s but that source is now disabled; falling through to chain",
                            mediaId,
                            override.name,
                        )
                        resolutionChain(settings)
                    }
            }
        Timber.tag(TAG).d("Multi-source resolve for %s | chain=%s", mediaId, chain.joinToString(",") { it.name })
        if (chain.isEmpty()) {
            Timber.tag(TAG).d("Multi-source skip: no sources to try (chain empty)")
            return null
        }
        if (lowDataMode) {
            Timber.tag(TAG).i("Low-data mode active; skipping lossless sources for %s", mediaId)
            return null
        }

        // Pool accounts are needed by every source; make sure the cached feed is in memory.
        runCatching { PoolAccountManager.loadCached(context) }
        runCatching { PoolAccountManager.refresh(context) }

        Timber.tag(TAG).d(
            "Source query built: title=\"%s\" artists=%s durationMs=%s",
            request.title,
            request.artists.joinToString("/"),
            request.durationMs?.toString() ?: "?",
        )

        val overrideIsSourceOverride = override != null && override != AudioSourceType.YOUTUBE
        var best: DirectStream? = null
        var bestSource: AudioSourceType? = null
        var bestScore = 0.0
        for (source in chain) {
            Timber.tag(TAG).d("Trying source: %s for \"%s\"", source.name, request.title)
            val stream: DirectStream? =
                when (source) {
                    AudioSourceType.TIDAL -> resolveTidalStream(context, request, settings)
                    AudioSourceType.QOBUZ -> resolveQobuzStream(request, settings)
                    AudioSourceType.DEEZER -> resolveDeezerStream(request, settings)
                    AudioSourceType.APPLE ->
                        resolveAppleStream(
                            context,
                            request,
                            settings,
                            trusted = overrideIsSourceOverride && override == AudioSourceType.APPLE,
                        )
                    // Amazon serves CENC-protected fragmented MP4 and ArchiveTune ships no
                    // decryption step, so there is no provider to call — fall through.
                    AudioSourceType.AMAZON,
                    AudioSourceType.QOBUZ_BACKUP,
                    AudioSourceType.JIOSAAVN,
                    AudioSourceType.YOUTUBE,
                    -> null
                }
            if (stream == null) {
                Timber.tag(TAG).d("Source %s did not resolve \"%s\"", source.name, request.title)
                continue
            }
            val match =
                if (overrideIsSourceOverride && source == override) {
                    Timber.tag(TAG).i(
                        "Source %s ACCEPTED for \"%s\" via per-song override (skipping metadata gate) [%s]",
                        source.name,
                        request.title,
                        stream.label,
                    )
                    TitleMatch.Result(true, 1.0, 1.0, 1.0, 1.0, "per-song override bypass")
                } else {
                    TitleMatch.evaluate(
                        wantedTitle = request.title,
                        wantedArtists = request.artists,
                        wantedAlbum = request.album,
                        wantedDurationMs = request.durationMs,
                        stream = stream,
                    )
                }
            if (!match.accepted) {
                Timber.tag(TAG).i(
                    "Source %s rejected for \"%s\": %s score=%.1f%% title=%.1f%% artist=%s duration=%s matched=\"%s\"",
                    source.name,
                    request.title,
                    match.reason,
                    match.score * 100,
                    match.title * 100,
                    match.artist?.let { "%.1f%%".format(it * 100) } ?: "?",
                    match.duration?.let { "%.1f%%".format(it * 100) } ?: "?",
                    stream.matchedTitle ?: "?",
                )
                continue
            }
            Timber.tag(TAG).d(
                "Source %s candidate for \"%s\": match %.1f%% (%s) [%s]",
                source.name,
                request.title,
                match.score * 100,
                match.reason,
                stream.label,
            )
            recordResolvedSource(mediaId, source)
            if (match.score > bestScore) {
                best = stream
                bestSource = source
                bestScore = match.score
            }
            if (best != null) break
        }

        val winningStream = best
        val winningSource = bestSource
        if (winningStream != null && winningSource != null) {
            Timber.tag(TAG).i(
                "Source WIN: %s resolved \"%s\" [%s] metadata match %.1f%% (%s)",
                winningSource.name,
                request.title,
                winningStream.label,
                bestScore * 100,
                winningStream.uri.take(80),
            )
            if (winningSource == AudioSourceType.TIDAL) {
                TidalAudioProvider.lastResolvedTrackId?.takeIf { it.isNotBlank() }?.let { probe ->
                    runCatching { context.dataStore.edit { prefs -> prefs[TidalLastProbeTrackKey] = probe } }
                }
            } else if (winningSource == AudioSourceType.QOBUZ) {
                QobuzAudioProvider.lastResolvedTrackId?.takeIf { it.isNotBlank() }?.let { probe ->
                    runCatching { context.dataStore.edit { prefs -> prefs[QobuzLastProbeTrackKey] = probe } }
                }
            }
            return applyDirectStream(mediaId, winningStream)
        }

        Timber.tag(TAG).w("No lossless source cleared the metadata match gate for \"%s\"; falling back", request.title)
        return null
    }

    private fun streamStillUsable(stream: DirectStream): Boolean {
        if (stream.uri.startsWith("file:")) {
            return runCatching { File(Uri.parse(stream.uri).path.orEmpty()).let { it.isFile && it.length() > 0L } }.getOrDefault(false)
        }
        return true
    }

    private fun recordResolvedSource(mediaId: String, source: AudioSourceType) {
        resolvedSourcesByMediaId.getOrPut(mediaId) { ConcurrentHashMap.newKeySet() }.add(source)
    }

    // ── Tidal ─────────────────────────────────────────────────────────────────────────────────

    private fun markTidalNeedsRelogin(context: Context) {
        runCatching { runBlocking { context.dataStore.edit { prefs -> prefs[TidalNeedsReloginKey] = true } } }
    }

    private fun persistRefreshedTidalToken(context: Context, refreshed: TidalAccountManager.TokenResult) {
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs[TidalAccessTokenKey] = refreshed.accessToken
                prefs[TidalTokenExpiryKey] = refreshed.expiresAtMillis
                refreshed.refreshToken?.let { prefs[TidalRefreshTokenKey] = it }
                refreshed.userId?.let { prefs[TidalUserIdKey] = it }
                refreshed.countryCode?.let { prefs[TidalCountryCodeKey] = it }
                prefs[TidalNeedsReloginKey] = false
            }
        }
    }

    private fun readTidalTokenState(context: Context): Triple<String, Long, Pair<String, String>> {
        val p = runBlocking { context.dataStore.data.first() }
        return Triple(
            p[TidalAccessTokenKey].orEmpty(),
            p[TidalTokenExpiryKey] ?: 0L,
            p[TidalRefreshTokenKey].orEmpty() to (p[TidalAuthFlowKey] ?: TidalAccountManager.FLOW_OAUTH),
        )
    }

    /** Port of `ensureValidTidalToken`: returns a usable personal token or null (public-instance fallback). */
    private fun ensureValidTidalToken(context: Context): String? {
        val (token, expiry, refreshAndFlow) = readTidalTokenState(context)
        val (refresh, flow) = refreshAndFlow
        if (token.isNotBlank() && expiry > System.currentTimeMillis() + 60_000L) return token
        if (refresh.isBlank()) {
            if (token.isBlank()) {
                // No account at all — nothing to flag.
                return null
            }
            Timber.tag(TAG).d("Tidal token expired/absent and no refresh token; account path unavailable")
            return token.ifBlank { null }
        }
        return synchronized(tidalTokenRefreshLock) {
            val (currentToken, currentExpiry, _) = readTidalTokenState(context)
            if (currentToken.isNotBlank() && currentExpiry > System.currentTimeMillis() + 60_000L) {
                Timber.tag(TAG).d("Tidal token already refreshed by another thread; reusing")
                return@synchronized currentToken
            }
            Timber.tag(TAG).d("Tidal access token expired; refreshing via stored refresh token (flow=%s)", flow)
            val refreshed =
                runCatching { runBlocking(Dispatchers.IO) { TidalAccountManager.refreshAccessToken(refresh, flow) } }
                    .onFailure { Timber.tag(TAG).w(it, "Tidal token refresh threw") }
                    .getOrNull()
            if (refreshed == null) {
                Timber.tag(TAG).w("Tidal token refresh failed; flagging re-login and falling back to public instances")
                markTidalNeedsRelogin(context)
                return@synchronized null
            }
            persistRefreshedTidalToken(context, refreshed)
            Timber.tag(TAG).i("Tidal token refreshed; valid for ~%ds", (refreshed.expiresAtMillis - System.currentTimeMillis()) / 1000)
            refreshed.accessToken
        }
    }

    /** Port of `refreshTidalToken` (force refresh after a 401). */
    private fun refreshTidalToken(context: Context, rejectedToken: String?): String? =
        synchronized(tidalTokenRefreshLock) {
            val (current, _, refreshAndFlow) = readTidalTokenState(context)
            val (refresh, flow) = refreshAndFlow
            if (current.isNotBlank() && current != rejectedToken) {
                Timber.tag(TAG).d("Tidal token already refreshed by another thread; reusing")
                return@synchronized current
            }
            if (refresh.isBlank()) {
                Timber.tag(TAG).w("401 from Tidal but no refresh token; account needs re-login")
                markTidalNeedsRelogin(context)
                return@synchronized null
            }
            Timber.tag(TAG).d("Force-refreshing Tidal token after 401 (flow=%s)", flow)
            val refreshed =
                runCatching { runBlocking(Dispatchers.IO) { TidalAccountManager.refreshAccessToken(refresh, flow) } }
                    .onFailure { Timber.tag(TAG).w(it, "Force refresh threw") }
                    .getOrNull()
            if (refreshed == null) {
                Timber.tag(TAG).w("Force refresh failed; account needs re-login")
                markTidalNeedsRelogin(context)
                return@synchronized null
            }
            persistRefreshedTidalToken(context, refreshed)
            Timber.tag(TAG).i("Tidal token force-refreshed after 401")
            refreshed.accessToken
        }

    private suspend fun resolveTidalStream(
        context: Context,
        request: Request,
        settings: SourceSettings,
    ): DirectStream? {
        val quality = settings.tidalQuality
        val cacheDir = context.cacheDir
        Timber.tag(TAG).d("Tidal resolve start | quality=%s accountFirst=%s", quality.name, settings.tidalAccountFirst)

        if (settings.tidalAccountFirst) {
            val apiQuality =
                when (quality) {
                    TidalAudioQuality.HI_RES_LOSSLESS -> "HI_RES_LOSSLESS"
                    TidalAudioQuality.FLAC -> "LOSSLESS"
                    TidalAudioQuality.AAC_320 -> "HIGH"
                }

            suspend fun attempt(accessToken: String, countryCode: String): DirectStream? =
                TidalAccountManager.resolveDirectStream(
                    accessToken = accessToken,
                    title = request.title,
                    artists = request.artists,
                    durationMs = request.durationMs,
                    audioQuality = apiQuality,
                    cacheDir = cacheDir,
                    countryCode = countryCode,
                )

            var token = ensureValidTidalToken(context)
            Timber.tag(TAG).d("Tidal account token available=%s", token != null)
            if (token != null) {
                val accountCountry = (prefs(context)[TidalCountryCodeKey].orEmpty()).ifBlank { "US" }
                val accountStream =
                    try {
                        attempt(token, accountCountry)
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (TidalAccountManager.isUnauthorized(e)) {
                            Timber.tag(TAG).w("Tidal account 401 (possibly wrapped); refreshing token + retrying")
                            Thread.interrupted()
                            val refreshed = refreshTidalToken(context, rejectedToken = token)
                            if (refreshed != null && refreshed != token) {
                                token = refreshed
                                runCatching { attempt(refreshed, accountCountry) }
                                    .onFailure { Timber.tag(TAG).w(it, "Tidal account retry failed for %s", request.mediaId) }
                                    .getOrNull()
                            } else {
                                null
                            }
                        } else {
                            Timber.tag(TAG).w(e, "Tidal account resolve failed for %s", request.mediaId)
                            null
                        }
                    }
                if (accountStream != null) return accountStream
            }

            for (poolAccount in PoolAccountManager.tidalAccounts()) {
                val poolCountry = poolAccount.countryCode?.trim()?.ifBlank { null } ?: "US"
                val poolStream =
                    runCatching { attempt(poolAccount.token, poolCountry) }
                        .onFailure {
                            if (it is kotlinx.coroutines.CancellationException) throw it
                            if (TidalAccountManager.isUnauthorized(it)) {
                                PoolAccountManager.report("tidal", "account", poolAccount.id, "dead")
                            } else {
                                Timber.tag(TAG).w(it, "Tidal pool account resolve failed for %s", request.mediaId)
                            }
                        }.getOrNull()
                if (poolStream != null) {
                    Timber.tag(TAG).d("Tidal resolved via pool account (premium=%s)", poolAccount.premium)
                    PoolAccountManager.noteAccountSuccess("tidal", poolAccount.id)
                    return poolStream
                }
                PoolAccountManager.noteAccountFailure("tidal", poolAccount.id)
            }
        }

        val configuredInstances = settings.tidalInstances
        val discoveredInstances = TidalInstanceHealthManager.healthyUrls(context)
        val mergedInstances =
            LinkedHashSet<String>().apply {
                addAll(configuredInstances)
                addAll(discoveredInstances)
            }.toList()
        Timber.tag(TAG).d(
            "Tidal public-instance fallback | configured=%d discovered=%d merged=%d",
            configuredInstances.size,
            discoveredInstances.size,
            mergedInstances.size,
        )
        if (mergedInstances.isEmpty()) {
            Timber.tag(TAG).d("Tidal skip: no instances configured (account path disabled or exhausted)")
            return null
        }
        TidalAudioProvider.setInstances(mergedInstances)
        val resolved =
            runCatching {
                TidalAudioProvider.resolve(
                    query =
                        TidalAudioProvider.Query(
                            mediaId = request.mediaId,
                            title = request.title,
                            artists = request.artists,
                            album = request.album,
                            isrc = request.isrc,
                            durationMs = request.durationMs,
                        ),
                    cacheDir = cacheDir,
                    preferAtmos = false,
                    preferLiveDash = true,
                    audioQuality = quality,
                )
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                Timber.tag(TAG).w(error, "TIDAL stream resolution failed for %s", request.mediaId)
            }.getOrNull() ?: return null

        return DirectStream(
            uri = resolved.mediaUri,
            mimeType = resolved.mimeType,
            codecs = resolved.codecs,
            contentLength = resolved.contentLength,
            label = "Tidal ${resolved.label}",
            source = AudioSourceType.TIDAL,
            matchedTitle = resolved.matchedTitle,
            matchedArtist = resolved.matchedArtist,
            matchedAlbum = resolved.matchedAlbum,
            matchedDurationMs = resolved.matchedDurationMs,
            sampleRate = resolved.sampleRate,
        )
    }

    // ── Qobuz ─────────────────────────────────────────────────────────────────────────────────

    private fun resolveQobuzStream(
        request: Request,
        settings: SourceSettings,
    ): DirectStream? {
        val userInstances = settings.qobuzInstances
        val discoveredInstances = runCatching { QobuzAudioProvider.discoverInstances() }.getOrDefault(emptyList())
        val configuredInstances =
            LinkedHashSet<String>().apply {
                addAll(userInstances)
                addAll(discoveredInstances)
            }.toList()

        val poolTokens =
            PoolAccountManager.qobuzAccounts().map {
                QobuzToken(
                    token = it.token,
                    appId = it.appId,
                    appSecret = it.appSecret,
                    label = "Source Pool",
                    subscription = if (it.premium) "premium" else "",
                    poolId = it.id,
                )
            }
        val configuredTokens =
            (QobuzToken.listFromJson(settings.qobuzTokensJson) + poolTokens)
                .distinctBy { it.token }
        if (configuredInstances.isEmpty() && configuredTokens.isEmpty()) {
            Timber.tag(TAG).d("Qobuz skip: no tokens or instances configured")
            return null
        }
        val formatId = settings.qobuzQuality.toFormatId()
        Timber.tag(TAG).d(
            "Qobuz resolve start | formatId=%d tokens=%d instances=%d",
            formatId,
            configuredTokens.size,
            configuredInstances.size,
        )
        QobuzAudioProvider.setTokens(configuredTokens)
        QobuzAudioProvider.setInstances(configuredInstances)
        return runCatching {
            QobuzAudioProvider.resolve(
                query =
                    QobuzAudioProvider.Query(
                        mediaId = request.mediaId,
                        title = request.title,
                        artists = request.artists,
                        album = request.album,
                        durationMs = request.durationMs,
                        directTrackId = null,
                    ),
                formatId = formatId,
            )
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "QOBUZ stream resolution failed for %s", request.mediaId)
        }.getOrNull()
    }

    // ── Deezer ────────────────────────────────────────────────────────────────────────────────

    private fun resolveDeezerStream(
        request: Request,
        settings: SourceSettings,
    ): DirectStream? {
        if (!DeezerAudioProvider.hasAccounts()) {
            Timber.tag(TAG).d("Deezer skip: no manual or pooled accounts available")
            return null
        }
        val quality = settings.deezerQuality
        Timber.tag(TAG).d("Deezer resolve start | quality=%s", quality.name)
        return runCatching {
            DeezerAudioProvider
                .resolve(
                    query =
                        DeezerAudioProvider.Query(
                            mediaId = request.mediaId,
                            title = request.title,
                            artists = request.artists,
                            album = request.album,
                            durationMs = request.durationMs,
                            isrc = request.isrc,
                        ),
                    format = quality.toFormatName(),
                )?.let { resolved ->
                    DirectStream(
                        uri = resolved.uri,
                        mimeType = resolved.mimeType,
                        codecs = resolved.codecs,
                        contentLength = resolved.contentLength,
                        label = resolved.label,
                        source = AudioSourceType.DEEZER,
                        matchedTitle = resolved.matchedTitle,
                        matchedArtist = resolved.matchedArtist,
                        matchedAlbum = resolved.matchedAlbum,
                        matchedDurationMs = resolved.matchedDurationMs,
                        sampleRate = resolved.sampleRate,
                        bitDepth = resolved.bitDepth,
                    )
                }
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "DEEZER stream resolution failed for %s", request.mediaId)
        }.getOrNull()
    }

    // ── Apple Music ───────────────────────────────────────────────────────────────────────────

    private suspend fun resolveAppleStream(
        context: Context,
        request: Request,
        settings: SourceSettings,
        trusted: Boolean,
    ): DirectStream? {
        if (AppleMusicAudioProvider.mediaUserToken() == null || AppleMusicAudioProvider.devToken() == null) {
            Timber.tag(TAG).d("Apple Music source: missing tokens (sign in via Settings → Lossless Sources → Apple Music)")
            return null
        }
        val appleQuality = settings.appleQuality
        val candidates =
            runCatching {
                AppleMusicAudioProvider.resolveCandidates(
                    title = request.title,
                    artists = request.artists,
                    album = request.album,
                    durationMs = request.durationMs,
                    quality = appleQuality,
                )
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Timber.tag(TAG).w(it, "Apple Music candidate search failed for %s", request.mediaId)
            }.getOrDefault(emptyList())
        if (candidates.isEmpty()) return null

        var winner: Pair<AppleMusicAudioProvider.AppleMusicStream, DirectStream>? = null
        var bestScore = -1.0
        for (candidate in candidates) {
            val stream =
                DirectStream(
                    uri = "apple-pending:${candidate.songId}",
                    mimeType = "audio/mp4",
                    codecs =
                        if (candidate.flavor.contains("ctrp", ignoreCase = true) &&
                            candidate.flavor.filter(Char::isDigit).toIntOrNull()?.let { it > 320 } == true
                        ) {
                            "alac"
                        } else {
                            "mp4a.40.2"
                        },
                    contentLength = candidate.contentLength,
                    label = "Apple Music ${candidate.flavor}",
                    source = AudioSourceType.APPLE,
                    matchedTitle = candidate.matchedTitle,
                    matchedArtist = candidate.matchedArtist,
                    matchedAlbum = candidate.matchedAlbum,
                    matchedDurationMs = candidate.matchedDurationMs,
                )
            val match =
                if (trusted) {
                    TitleMatch.Result(true, 1.0, 1.0, 1.0, 1.0, "per-song override bypass")
                } else {
                    TitleMatch.evaluate(
                        wantedTitle = request.title,
                        wantedArtists = request.artists,
                        wantedAlbum = request.album,
                        wantedDurationMs = request.durationMs,
                        stream = stream,
                    )
                }
            if (match.accepted && match.score > bestScore) {
                winner = candidate to stream
                bestScore = match.score
            }
        }
        val (candidate, placeholder) = winner ?: return null

        return try {
            val file =
                appleStreamFile(context, request.mediaId, appleQuality) {
                    AppleMusicVirtualStream.build(httpClient, candidate.playlistUrl, candidate.keyIdHex).bytes
                }
            AppleMusicDrm.register(request.mediaId, AppleTrackDrmInfo(adamId = candidate.songId, drmUri = candidate.drmUri))
            Timber.tag(TAG).i("Apple Music resolved [%s] for \"%s\" (%d KB)", placeholder.label, request.title, file.length() / 1024)
            placeholder.copy(uri = Uri.fromFile(file).toString(), contentLength = file.length())
        } catch (err: Throwable) {
            if (err is kotlinx.coroutines.CancellationException) throw err
            Timber.tag(TAG).w(err, "Apple Music virtual stream failed for \"%s\"", request.title)
            null
        }
    }

    private fun appleStreamFile(
        context: Context,
        mediaId: String,
        quality: AppleMusicQuality,
        build: () -> ByteArray,
    ): File {
        val dir = File(context.cacheDir, "applemusic").apply { mkdirs() }
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= APPLE_CACHE_LIMIT_BYTES) break
            total -= f.length()
            f.delete()
        }
        val safeId = mediaId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val out = File(dir, "${safeId}_${quality.name.lowercase(Locale.US)}_v2.m4a")
        if (out.exists() && out.length() > 0) return out
        out.writeBytes(build())
        return out
    }

    // ── Result publication (port of applyDirectStream + persistDirectStreamFormat) ────────────

    private fun applyDirectStream(mediaId: String, stream: DirectStream): Result {
        Timber.tag(TAG).i("Using %s stream for %s: %s", stream.source, mediaId, stream.label)
        directStreamCache[sourceCacheKey(stream.source, mediaId)] =
            CachedDirectStream(stream = stream, expiresAtMs = System.currentTimeMillis() + DIRECT_STREAM_CACHE_TTL_MS)
        return publish(mediaId, stream, fromCache = false)
    }

    private fun publish(mediaId: String, stream: DirectStream, fromCache: Boolean): Result {
        val label = stream.label.uppercase(Locale.US)
        val mime = stream.mimeType.substringBefore(";").trim().ifBlank { "audio/flac" }
        val codecs =
            stream.codecs.ifBlank {
                stream.mimeType.substringAfter("codecs=", "").removeSurrounding("\"").ifBlank { "flac" }
            }
        val sampleRate =
            stream.sampleRate?.takeIf { it > 0 }
                ?: when {
                    label.contains("HI_RES") || label.contains("MASTER") || label.contains("MQA") -> 96_000
                    label.contains("LOSSLESS") || codecs.contains("flac", true) || codecs.contains("alac", true) -> 44_100
                    else -> null
                }
        val knownContentLength = stream.contentLength?.takeIf { it > 0L }
        val bitrate =
            measuredBitrate(knownContentLength, stream.matchedDurationMs)
                ?: stream.pcmBitrateOrNull()
                ?: when {
                    label.contains("HI_RES") || label.contains("MASTER") || label.contains("MQA") -> 2_304_000
                    label.contains("LOSSLESS") || codecs.contains("flac", true) || codecs.contains("alac", true) -> 1_411_000
                    label.contains("HIGH") -> 320_000
                    else -> 0
                }
        val result =
            Result(
                mediaId = mediaId,
                uri = stream.uri,
                mimeType = mime,
                codecs = codecs,
                bitrate = bitrate,
                sampleRate = sampleRate,
                bitDepth = stream.bitDepth,
                contentLength = knownContentLength,
                label = stream.label,
                source = stream.source,
            )
        resolvedByMediaId[mediaId] = result
        uriFreshness[stream.uri] = System.currentTimeMillis() + URI_FRESHNESS_MS
        _lastResolved.value = result

        // ArchiveTune back-fills the byte length with a HEAD request so the bitrate shown in the
        // player is measured rather than guessed. Only meaningful for plain HTTP(S) streams.
        if (!fromCache && knownContentLength == null && stream.uri.startsWith("http", ignoreCase = true)) {
            ioScope.launch {
                runCatching {
                    val headRequest = okhttp3.Request.Builder().url(stream.uri).head().build()
                    httpClient.newCall(headRequest).execute().use { response ->
                        val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
                        if (len > 0L) {
                            val backfilled = measuredBitrate(len, stream.matchedDurationMs)
                            val current = resolvedByMediaId[mediaId]
                            if (current != null && current.uri == stream.uri) {
                                val updated = current.copy(contentLength = len, bitrate = backfilled ?: current.bitrate)
                                resolvedByMediaId[mediaId] = updated
                                if (_lastResolved.value?.mediaId == mediaId) _lastResolved.value = updated
                            }
                        }
                    }
                }.onFailure { err -> Timber.tag(TAG).d(err, "HEAD request for content length failed: %s", stream.uri) }
            }
        }
        return result
    }

    private fun measuredBitrate(
        contentLength: Long?,
        durationMs: Long?,
    ): Int? {
        val bytes = contentLength?.takeIf { it > 0L } ?: return null
        val millis = durationMs?.takeIf { it > 0L } ?: return null
        val bitsPerSecond = bytes * 8_000L / millis
        return bitsPerSecond.takeIf { it in 1L..50_000_000L }?.toInt()
    }

    private fun sourceCacheKey(
        source: AudioSourceType,
        mediaId: String,
    ): String = "${source.name.lowercase(Locale.US)}:$mediaId"
}
