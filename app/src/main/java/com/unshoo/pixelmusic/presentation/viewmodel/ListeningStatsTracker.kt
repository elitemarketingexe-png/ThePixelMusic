package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.unshoo.pixelmusic.data.DailyMixManager
import com.unshoo.pixelmusic.data.database.EngagementDao
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.SongDownloadWorker
import com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import com.unshoo.pixelmusic.utils.YouTubeIdUtils

/**
 * Tracks listening statistics for songs.
 * Extracted from PlayerViewModel to reduce its size and improve modularity.
 *
 * Responsibilities:
 * - Track active listening sessions
 * - Record play statistics when session ends
 * - Handle voluntary vs automatic plays
 */
@Singleton
class ListeningStatsTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dailyMixManager: DailyMixManager,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val engagementDao: EngagementDao,
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private var currentSession: ActiveSession? = null
    private var pendingVoluntarySongId: String? = null
    @Volatile private var telemetryCpn: String? = null
    @Volatile private var telemetryLastReportedTimeMs: Long = 0L
    @Volatile private var telemetrySessionStartTimeMs: Long = 0L
    @Volatile private var isPlaybackStartReported = false
    @Volatile private var isWatchCompleted = false
    private var scope: CoroutineScope? = null
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _playbackHistory = MutableStateFlow<List<PlaybackStatsRepository.PlaybackHistoryEntry>>(emptyList())
    val playbackHistory: StateFlow<List<PlaybackStatsRepository.PlaybackHistoryEntry>> = _playbackHistory.asStateFlow()

    /**
     * Must be called to set the coroutine scope for async operations.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        val activeScope = scope
        if (activeScope == null || activeScope.coroutineContext[Job]?.isActive != true) {
            scope = coroutineScope
        }
        coroutineScope.launch(Dispatchers.IO) {
            _playbackHistory.value = playbackStatsRepository.loadPlaybackHistory(
                limit = MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS
            )
            // Merge authenticated YouTube Music history on top of local history so
            // Recently Played shows local songs + YT Music synced history together.
            refreshMergedYoutubeHistory()
        }
    }

    /**
     * Pull FEmusic_history from YouTube Music (when logged in) and merge with local
     * playback history. Local entries win on id collisions so offline plays stay accurate.
     */
    @Volatile private var lastMergedHistoryAtMs = 0L
    @Volatile private var mergeInFlight = false

    fun refreshMergedYoutubeHistory() {
        val activeScope = scope ?: persistenceScope
        activeScope.launch(Dispatchers.IO) {
            try {
                _playbackHistory.value = playbackStatsRepository.loadPlaybackHistory(
                    limit = MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to load local playback history")
            }
        }
    }

    @Synchronized
    fun onVoluntarySelection(songId: String) {
        pendingVoluntarySongId = songId
    }

    fun onSongChanged(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        onTrackChanged(
            songId = song?.id,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = song?.duration ?: 0L,
            isPlaying = isPlaying,
            title = song?.title,
            artist = song?.displayArtist,
            thumbnail = song?.albumArtUriString,
            genre = song?.genre,
            album = song?.album
        )
    }

    @Synchronized
    fun onTrackChanged(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        onTrackChanged(
            songId = songId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = 0L,
            isPlaying = isPlaying
        )
    }

    private suspend fun resolveYtId(songId: String): String? {
        return if (songId.startsWith("youtube_")) songId.removePrefix("youtube_")
        else {
            val numericId = songId.toLongOrNull()
            if (numericId != null && numericId < 0) {
                val entity = musicDao.getSongByIdOnce(numericId)
                if (entity?.contentUriString?.startsWith("youtube://") == true) entity.contentUriString.removePrefix("youtube://") else null
            } else null
        }
    }

    @Synchronized
    fun onTrackChanged(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        fallbackDurationMs: Long,
        isPlaying: Boolean,
        title: String? = null,
        artist: String? = null,
        thumbnail: String? = null,
        genre: String? = null,
        album: String? = null
    ) {
        finalizeCurrentSession()
        val safeSongId = songId?.takeIf { it.isNotBlank() }
        if (safeSongId == null) {
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        val normalizedDuration = normalizeDuration(durationMs, fallbackDurationMs)

        currentSession = ActiveSession(
            songId = safeSongId,
            totalDurationMs = normalizedDuration,
            startedAtEpochMs = nowEpoch,
            lastKnownPositionMs = positionMs.coerceAtLeast(0L),
            accumulatedListeningMs = 0L,
            lastRealtimeMs = nowRealtime,
            lastUpdateEpochMs = nowEpoch,
            isPlaying = isPlaying,
            isVoluntary = pendingVoluntarySongId == safeSongId,
            title = title,
            artist = artist,
            thumbnail = thumbnail,
            genre = genre,
            album = album
        )

        // Instant local history head so Recently Played matches the current song immediately
        // (before the full session finalize / YT remote sync completes).
        if (!title.isNullOrBlank()) {
            val optimistic = PlaybackStatsRepository.PlaybackHistoryEntry(
                songId = safeSongId,
                timestamp = nowEpoch,
                title = title,
                artist = artist,
                thumbnail = thumbnail
            )
            _playbackHistory.update { current ->
                val withoutDup = current.filterNot { it.songId == safeSongId }
                (listOf(optimistic) + withoutDup).take(MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS)
            }
        }

        persistenceScope.launch(Dispatchers.IO) {
            runCatching {
                val ytId = resolveYtId(safeSongId)
                if (ytId != null) {
                    // BUGFIX: CPN generation was gated behind SEND_REDUNDANT_REMOTE_YOUTUBE_TELEMETRY.
                    // When that flag was false, telemetryCpn stayed null forever, causing
                    // onProgress() to early-return on `val cpn = telemetryCpn ?: return` every
                    // tick — no watchtime was ever accumulated, so finalizeCurrentSession()
                    // always saw accumulatedListeningMs < 15s and never persisted to history.
                    // CPN is always generated now; remote pings are still gated separately.
                    val cpn = (1..16).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".random() }.joinToString("")
                    telemetryCpn = cpn
                    telemetryLastReportedTimeMs = 0L
                    telemetrySessionStartTimeMs = System.currentTimeMillis()
                    isPlaybackStartReported = false
                    isWatchCompleted = false

                    // MusicService's YouTubeTelemetryManager is the sole remote history writer.
                    // Only resolve tracking URLs and send remote pings in redundant mode.
                    if (!SEND_REDUNDANT_REMOTE_YOUTUBE_TELEMETRY) return@launch

                    // SpatialFlow parity: resolve tracking URLs quickly, then:
                    //  1) send stats/playback start ping (with auth headers via sendTelemetryPing)
                    //  2) call YouTube.registerPlayback when we have a real tracking base URL
                    // Both are needed for the currently playing song to appear in YT Music history.
                    var trackingUrl: String? =
                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[ytId]
                    var watchtimeUrl: String? =
                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.watchtimeTrackingCache[ytId]
                    if (trackingUrl == null) {
                        // Wait up to ~1.2s for stream resolve to populate tracking cache.
                        repeat(12) {
                            kotlinx.coroutines.delay(100L)
                            trackingUrl =
                                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[ytId]
                            watchtimeUrl =
                                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.watchtimeTrackingCache[ytId]
                            if (trackingUrl != null) return@repeat
                        }
                    }

                    // If still missing, fetch a lightweight WEB_REMIX player response just for tracking URLs
                    // (same fallback SpatialFlow / registerYoutubePlaybackHistory uses).
                    if (trackingUrl.isNullOrBlank()) {
                        runCatching {
                            val signatureTimestamp = unshoo.ianshulyadav.pixelmusic.innertube.NewPipeUtils
                                .getSignatureTimestamp(ytId)
                                .getOrNull()
                            val playerRes = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.player(
                                videoId = ytId,
                                playlistId = null,
                                client = unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.WEB_REMIX,
                                signatureTimestamp = signatureTimestamp,
                                setLogin = true
                            ).getOrNull()
                            trackingUrl = playerRes?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                            watchtimeUrl = playerRes?.playbackTracking?.videostatsWatchtimeUrl?.baseUrl
                            if (!trackingUrl.isNullOrBlank()) {
                                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                                    .playbackTrackingCache[ytId] = trackingUrl
                            }
                            if (!watchtimeUrl.isNullOrBlank()) {
                                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                                    .watchtimeTrackingCache[ytId] = watchtimeUrl
                            }
                        }
                    }

                    val finalTrackingUrl = (trackingUrl ?: "")
                        .replace("https://s.youtube.com", "https://music.youtube.com")
                        .ifBlank {
                            "https://music.youtube.com/api/stats/playback?ns=yt&el=detailpage&docid=$ytId"
                        }

                    val separator = if (finalTrackingUrl.contains("?")) "&" else "?"
                    val rtSec = (System.currentTimeMillis() - telemetrySessionStartTimeMs) / 1000
                    val startUrl =
                        "${finalTrackingUrl}${separator}ver=2&c=WEB_REMIX&cver=1.20260531.05.00" +
                            "&cplayer=UNIPLAYER&cpn=$cpn&rt=$rtSec&docid=$ytId"

                    if (SEND_REDUNDANT_REMOTE_YOUTUBE_TELEMETRY) {
                        unshoo.ianshulyadav.pixelmusic.innertube.YouTube.sendTelemetryPing(startUrl)
                    }
                    isPlaybackStartReported = true

                    // Full registerPlayback path (InnerTube-authenticated) when we have a real tracking URL.
                    if (SEND_REDUNDANT_REMOTE_YOUTUBE_TELEMETRY && !trackingUrl.isNullOrBlank()) {
                        runCatching {
                            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.registerPlayback(
                                playlistId = null,
                                playbackTracking = trackingUrl,
                                videoId = ytId
                            )
                        }.onSuccess {
                            timber.log.Timber.d("YT history registerPlayback OK for %s", ytId)
                        }.onFailure {
                            timber.log.Timber.w(it, "YT history registerPlayback failed for %s", ytId)
                        }
                    }

                    // Immediate first watchtime heartbeat at t≈1s (SpatialFlow does this at 1s).
                    kotlinx.coroutines.delay(1_000L)
                    if (telemetryCpn == cpn && currentSession?.songId == safeSongId) {
                        sendWatchtimePingInternal(
                            videoId = ytId,
                            st = 0,
                            et = 1,
                            cpn = cpn,
                            sessionStartTimeMs = telemetrySessionStartTimeMs,
                            isPaused = false
                        )
                        telemetryLastReportedTimeMs = 1_000L
                    }
                }
            }.onFailure {
                timber.log.Timber.w(it, "YT telemetry start failed")
            }
        }
        if (pendingVoluntarySongId == safeSongId) {
            pendingVoluntarySongId = null
        }
    }

    private suspend fun sendWatchtimePingInternal(
        videoId: String,
        st: Long,
        et: Long,
        cpn: String,
        sessionStartTimeMs: Long,
        isPaused: Boolean
    ) {
        runCatching {
            val cachedWatchUrl = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.watchtimeTrackingCache[videoId]?.replace("https://s.youtube.com", "https://music.youtube.com")
                ?: "https://music.youtube.com/api/stats/watchtime?ns=yt&el=detailpage&docid=$videoId"
            
            // BUGFIX: capture totalDurationMs safely — currentSession is accessed from
            // persistenceScope while @Synchronized methods mutate it. Snapshot the value
            // at call time to avoid a data race.
            val capturedDurationMs = currentSession?.totalDurationMs ?: 0L
            val lengthSec = if (capturedDurationMs > 0) capturedDurationMs / 1000 else 0L
            val rtSec = (System.currentTimeMillis() - sessionStartTimeMs) / 1000
            val state = if (isPaused) "paused" else if (et >= lengthSec * 0.95 && lengthSec > 0) "ended" else "playing"
            val separator = if (cachedWatchUrl.contains("?")) "&" else "?"

            val fullUrl = "$cachedWatchUrl${separator}cpn=$cpn&state=$state&st=$st&et=$et&cmt=$et&rt=$rtSec&lact=1&len=$lengthSec&ver=2&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER&afmt=251&muted=0&volume=100"
            if (SEND_REDUNDANT_REMOTE_YOUTUBE_TELEMETRY) {
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.sendTelemetryPing(fullUrl)
            }
        }
    }

    @Synchronized
    fun onPlayStateChanged(isPlaying: Boolean, positionMs: Long) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        accumulateRealtimeListening(session, nowRealtime)
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()

        val songId = session.songId
        val cpn = telemetryCpn
        if (cpn != null) {
            val startTimeMs = telemetrySessionStartTimeMs
            val lastReportedSec = telemetryLastReportedTimeMs / 1000
            val positionSec = positionMs / 1000
            if (positionSec > lastReportedSec) {
                persistenceScope.launch(Dispatchers.IO) {
                    val ytId = resolveYtId(songId)
                    if (ytId != null) {
                        sendWatchtimePingInternal(ytId, lastReportedSec, positionSec, cpn, startTimeMs, !isPlaying)
                    }
                }
            }
            // BUGFIX: Do NOT null telemetryCpn on pause. Temporary pauses (buffering,
            // audio focus loss, user pause) are normal and playback will resume.
            // Nulling the CPN here causes onProgress to early-return on resume,
            // permanently stopping watchtime accumulation for this song's session.
            // The CPN is properly cleaned up in finalizeCurrentSession() when the
            // track actually changes.
        }
    }

    @Synchronized
    fun onProgress(positionMs: Long, isPlaying: Boolean) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        accumulateRealtimeListening(session, nowRealtime)
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()

        val songId = session.songId
        if (isPlaying) {
            val durationMs = session.totalDurationMs
            val positionSec = positionMs / 1000
            val lastReportedSec = telemetryLastReportedTimeMs / 1000
            val cpn = telemetryCpn ?: return
            val startTimeMs = telemetrySessionStartTimeMs

            var pingSt: Long? = null
            var pingEt: Long? = null

            if (Math.abs(positionMs - telemetryLastReportedTimeMs) > 2000L) {
                val prevPos = lastReportedSec
                val preSeekPos = positionSec
                if (preSeekPos > prevPos) {
                    pingSt = prevPos
                    pingEt = preSeekPos
                }
                telemetryLastReportedTimeMs = positionMs
            } else if (lastReportedSec == 0L && positionSec >= 1L) {
                pingSt = 0L
                pingEt = positionSec
                telemetryLastReportedTimeMs = positionMs
            } else if (positionSec - lastReportedSec >= 30L) {
                pingSt = lastReportedSec
                pingEt = positionSec
                telemetryLastReportedTimeMs = positionMs
            } else if (durationMs > 0L && !isWatchCompleted) {
                val completionRatio = positionMs.toFloat() / durationMs.toFloat()
                if (completionRatio >= 0.96f) {
                    pingSt = lastReportedSec
                    pingEt = positionSec
                    telemetryLastReportedTimeMs = positionMs
                    isWatchCompleted = true
                }
            }

            if (pingSt != null && pingEt != null) {
                persistenceScope.launch(Dispatchers.IO) {
                    val ytId = resolveYtId(songId)
                    if (ytId != null) {
                        sendWatchtimePingInternal(ytId, pingSt, pingEt, cpn, startTimeMs, false)
                    }
                }
            }
        }
    }

    fun ensureSession(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        ensureSession(
            songId = song?.id,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = song?.duration ?: 0L,
            isPlaying = isPlaying,
            title = song?.title,
            artist = song?.displayArtist,
            thumbnail = song?.albumArtUriString,
            genre = song?.genre,
            album = song?.album
        )
    }

    @Synchronized
    fun ensureSession(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        ensureSession(
            songId = songId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = 0L,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun ensureSession(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        fallbackDurationMs: Long,
        isPlaying: Boolean,
        title: String? = null,
        artist: String? = null,
        thumbnail: String? = null,
        genre: String? = null,
        album: String? = null
    ) {
        val safeSongId = songId?.takeIf { it.isNotBlank() }
        if (safeSongId == null) {
            finalizeCurrentSession()
            return
        }
        val existing = currentSession
        if (existing?.songId == safeSongId) {
            updateDuration(normalizeDuration(durationMs, fallbackDurationMs))
            val nowRealtime = SystemClock.elapsedRealtime()
            accumulateRealtimeListening(existing, nowRealtime)
            existing.isPlaying = isPlaying
            existing.lastRealtimeMs = nowRealtime
            existing.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
            existing.lastUpdateEpochMs = System.currentTimeMillis()
            return
        }
        onTrackChanged(
            songId = safeSongId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = fallbackDurationMs,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            thumbnail = thumbnail,
            genre = genre,
            album = album
        )
    }

    @Synchronized
    fun updateDuration(durationMs: Long) {
        val session = currentSession ?: return
        if (durationMs > 0 && durationMs != C.TIME_UNSET) {
            session.totalDurationMs = durationMs
        }
    }

    @Synchronized
    fun finalizeCurrentSession(forceSynchronousPersistence: Boolean = false) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        accumulateRealtimeListening(session, nowRealtime)
        val listened = session.accumulatedListeningMs.coerceAtLeast(0L)
        val trackDuration = session.totalDurationMs
        val completedPlayback = isCompletedPlayback(
            listenedMs = listened,
            lastPositionMs = session.lastKnownPositionMs,
            durationMs = trackDuration
        )
        val countedDuration = if (completedPlayback) {
            if (trackDuration > 0) listened.coerceAtMost(trackDuration) else listened
        } else {
            0L
        }

        val songId = session.songId
        val cpn = telemetryCpn
        if (cpn != null) {
            val startTimeMs = telemetrySessionStartTimeMs
            val lastReportedSec = telemetryLastReportedTimeMs / 1000
            val positionSec = session.lastKnownPositionMs / 1000
            if (positionSec > lastReportedSec) {
                persistenceScope.launch(Dispatchers.IO) {
                    val ytId = resolveYtId(songId)
                    if (ytId != null) {
                        sendWatchtimePingInternal(ytId, lastReportedSec, positionSec, cpn, startTimeMs, true)
                    }
                }
            }
            telemetryCpn = null
        }
        if (completedPlayback) {
            val rawEndTimestamp = when {
                session.isPlaying -> nowEpoch
                session.lastUpdateEpochMs > 0L -> session.lastUpdateEpochMs
                else -> session.startedAtEpochMs + countedDuration
            }
            val timestamp = rawEndTimestamp
                .coerceAtLeast(session.startedAtEpochMs.coerceAtLeast(0L))
                .coerceAtMost(nowEpoch)
            val songId = session.songId
            val historyEntry = PlaybackStatsRepository.PlaybackHistoryEntry(
                songId = songId,
                timestamp = timestamp,
                title = session.title,
                artist = session.artist,
                thumbnail = session.thumbnail
            )
            _playbackHistory.update { current ->
                // BUGFIX: dedupe by songId (same as onTrackChanged's optimistic insert)
                // to prevent duplicate Recently Played rows for the same song.
                val withoutDup = current.filterNot { it.songId == songId }
                (listOf(historyEntry) + withoutDup).take(MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS)
            }
            persistPlayback(
                songId = songId,
                listened = countedDuration,
                timestamp = timestamp,
                forceSynchronous = forceSynchronousPersistence,
                title = session.title,
                artist = session.artist,
                thumbnail = session.thumbnail,
                genre = session.genre,
                album = session.album
            )
        } else if (listened >= 1000L) {
            // Log as negative feedback skip signal if song was started but skipped before 15 seconds.
            com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.registerSkip(session.songId)
        }
        currentSession = null
        if (pendingVoluntarySongId == session.songId) {
            pendingVoluntarySongId = null
        }
    }

    @Synchronized
    fun onPlaybackStopped() {
        finalizeCurrentSession()
    }

    @Synchronized
    fun onCleared() {
        finalizeCurrentSession(forceSynchronousPersistence = true)
        // BUGFIX: Do NOT null `scope` here. ListeningStatsTracker is @Singleton and
        // MusicService may still be playing (foreground service survives Activity/ViewModel
        // lifecycle). Nulling scope breaks history recording for the rest of the session.
        // The scope will be re-set when PlayerViewModel is recreated.
    }

    private fun persistPlayback(
        songId: String,
        listened: Long,
        timestamp: Long,
        forceSynchronous: Boolean,
        title: String? = null,
        artist: String? = null,
        thumbnail: String? = null,
        genre: String? = null,
        album: String? = null
    ) {
        if (forceSynchronous) {
            // BUGFIX (freeze after long idle): this runs inside finalizeCurrentSession(), which
            // is @Synchronized - so runBlocking here held ListeningStatsTracker's monitor for as
            // long as persistPlaybackInternal() took, with NO upper bound. finalizeCurrentSession
            // is called from MusicService.onDestroy()/onTaskRemoved() on the main thread; if the
            // DB/DataStore write ever stalled (e.g. right after a long idle period, when the
            // process/disk is coming back from a suspended state), the main thread - and with it
            // every other @Synchronized method on this tracker that anything else needed to call
            // - would hang indefinitely. That matches "app completely freezes, has to be force-
            // cleared from recents": swiping away doesn't actually kill a hung foreground-service
            // process. withTimeoutOrNull() guarantees this can never block longer than
            // FORCE_PERSIST_TIMEOUT_MS, so the monitor is always released within a bounded time.
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(FORCE_PERSIST_TIMEOUT_MS) {
                        persistPlaybackInternal(
                            songId = songId,
                            listened = listened,
                            timestamp = timestamp,
                            title = title,
                            artist = artist,
                            thumbnail = thumbnail,
                            genre = genre,
                            album = album
                        )
                    } ?: Timber.w("Synchronous listening-session persist timed out after %dms for song=%s", FORCE_PERSIST_TIMEOUT_MS, songId)
                }.onFailure { throwable ->
                    Timber.e(throwable, "Failed to persist listening session synchronously for song=%s", songId)
                }
            }
        } else {
            persistenceScope.launch {
                runCatching {
                    persistPlaybackInternal(
                        songId = songId,
                        listened = listened,
                        timestamp = timestamp,
                        title = title,
                        artist = artist,
                        thumbnail = thumbnail,
                        genre = genre,
                        album = album
                    )
                }.onFailure { throwable ->
                    Timber.e(throwable, "Failed to persist listening session for song=%s", songId)
                }
            }
        }
    }

    private suspend fun persistPlaybackInternal(
        songId: String,
        listened: Long,
        timestamp: Long,
        title: String? = null,
        artist: String? = null,
        thumbnail: String? = null,
        genre: String? = null,
        album: String? = null
    ) {
        dailyMixManager.recordPlay(
            songId = songId,
            songDurationMs = listened,
            timestamp = timestamp
        )
        playbackStatsRepository.recordPlayback(
            songId = songId,
            durationMs = listened,
            timestamp = timestamp,
            title = title,
            artist = artist,
            thumbnail = thumbnail,
            genre = genre,
            album = album
        )
        // BUGFIX: removed the third remote writer that was here. It sent a state=ended
        // ping with a brand-new random CPN and len = listened/1000 (the listened time, not
        // the track length) — wrong data on a session YouTube has never seen a start ping
        // for. YouTubeTelemetryManager is the sole remote writer.
        if (userPreferencesRepository.cacheMostPlayedSongsOfflineFlow.first()) {
            triggerAutoCacheIfNeeded(songId)
        }
    }

    /**
     * Checks whether a YouTube song has been played enough times to warrant
     * automatic offline caching. Silently enqueues [SongDownloadWorker] if:
     * - The song is sourced from YouTube (content URI starts with "youtube://")
     * - Play count has reached or exceeded [AUTO_CACHE_PLAY_COUNT_THRESHOLD]
     * - The song is not already cached locally (file_path is blank)
     */
    private suspend fun triggerAutoCacheIfNeeded(songId: String) {
        try {
            val playCount = engagementDao.getPlayCount(songId) ?: return
            if (playCount < AUTO_CACHE_PLAY_COUNT_THRESHOLD) return

            // Resolve the Room numeric ID to look up the song entity
            val numericId = songId.toLongOrNull() ?: run {
                if (songId.startsWith("youtube_")) {
                    val ytId = songId.removePrefix("youtube_")
                    YouTubeIdUtils.toUnifiedYoutubeSongId(ytId)
                } else null
            } ?: return
            val songEntity = musicDao.getSongByIdOnce(numericId) ?: return

            // Only auto-cache YouTube-streamed songs that aren't already downloaded
            val contentUri = songEntity.contentUriString
            if (!contentUri.startsWith("youtube://")) return
            if (songEntity.filePath.isNotBlank()) return // Already cached

            val youtubeId = contentUri.removePrefix("youtube://")
            if (youtubeId.isBlank()) return

            val workName = "auto_cache_$youtubeId"
            val request = OneTimeWorkRequestBuilder<SongDownloadWorker>()
                .setInputData(workDataOf(SongDownloadWorker.SONG_KEY to youtubeId))
                .addTag("auto_cache")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
            Timber.d("Auto-cache triggered for YouTube song $youtubeId (play count = $playCount)")
        } catch (e: Exception) {
            Timber.w(e, "Auto-cache check failed for song $songId")
        }
    }

    private fun accumulateRealtimeListening(session: ActiveSession, nowRealtime: Long) {
        if (!session.isPlaying) return
        val delta = (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
        if (delta > 0L) {
            session.accumulatedListeningMs += delta
        }
    }

    private fun normalizeDuration(durationMs: Long, fallbackDurationMs: Long): Long {
        return when {
            durationMs > 0 && durationMs != C.TIME_UNSET -> durationMs
            fallbackDurationMs > 0 && fallbackDurationMs != C.TIME_UNSET -> fallbackDurationMs
            else -> 0L
        }
    }

    /**
     * Counts a play only when the device actually listened through the track.
     *
     * Both conditions are intentional: position alone can be satisfied by seeking to the end,
     * while elapsed listening alone can be satisfied by a stuck stream or bad remote duration.
     * Requiring both keeps YouTube/Cast metadata and remote watch-history timing from creating
     * local plays. A small end tolerance accounts for players transitioning just before their
     * final progress callback.
     */
    private fun isCompletedPlayback(
        listenedMs: Long,
        lastPositionMs: Long,
        durationMs: Long
    ): Boolean {
        // Minimum required listening threshold: 15 seconds (or 50% of short tracks under 30s)
        val requiredThresholdMs = if (durationMs > 0L && durationMs != C.TIME_UNSET && durationMs < 30_000L) {
            (durationMs * 0.50f).toLong().coerceAtLeast(3_000L)
        } else {
            MIN_PLAYBACK_LISTEN_MS
        }
        // STRICT: Require ACTUAL elapsed listening time (listenedMs).
        // Seeking/scrubbing position (lastPositionMs) is NEVER used to validate a completed play.
        return listenedMs >= requiredThresholdMs
    }

    companion object {
        /** Minimum elapsed listening duration (15 seconds) to count a track play in history. */
        private const val MIN_PLAYBACK_LISTEN_MS = 15_000L
        private const val MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS = 500
        /** Number of plays before a YouTube song is auto-downloaded for offline use. */
        private const val AUTO_CACHE_PLAY_COUNT_THRESHOLD = 3

        /**
         * Remote YouTube history writing is owned exclusively by MusicService's
         * YouTubeTelemetryManager.
         *
         * BUGFIX (history syncs for a few songs then stops): this was `true`, which made this
         * class a *second*, completely independent remote writer — its own random cpn, its own
         * session clock, its own st/et bookkeeping — pinging the same docid at overlapping
         * times as YouTubeTelemetryManager. persistPlaybackInternal() then fired a *third*
         * `state=ended` ping with yet another fresh cpn and `len` set to the accumulated
         * listening time instead of the track length. YouTube deduplicates and rate-limits per
         * (docid, cpn); a docid arriving under three conflicting session identities, one of
         * them with an impossible length, is exactly the shape that gets dropped rather than
         * committed to watch history — and it degrades progressively as more sessions pile up,
         * which is why the first few songs land and later ones do not.
         *
         * Keeping this `false` restores the single-writer design the rest of the file already
         * documents. Local stats/Recently Played are unaffected: they never went through this
         * flag.
         */
        // BUGFIX: set to false. YouTubeTelemetryManager is the sole remote history writer.
        // Having this true creates a SECOND concurrent cpn session for the same docid,
        // plus a third random-cpn ended ping in persistPlaybackInternal — both conflict
        // with the canonical session and confuse YouTube's dedup/commit logic.
        private const val SEND_REDUNDANT_REMOTE_YOUTUBE_TELEMETRY = false

        // BUGFIX (freeze after long idle): upper bound for the synchronous, monitor-holding
        // persistence path in persistPlayback(forceSynchronous = true). See the call site for
        // the full explanation.
        private const val FORCE_PERSIST_TIMEOUT_MS = 5_000L
    }
}

/**
 * Represents an active listening session for a song.
 */
data class ActiveSession(
    val songId: String,
    var totalDurationMs: Long,
    val startedAtEpochMs: Long,
    var lastKnownPositionMs: Long,
    var accumulatedListeningMs: Long,
    var lastRealtimeMs: Long,
    var lastUpdateEpochMs: Long,
    var isPlaying: Boolean,
    val isVoluntary: Boolean,
    val title: String? = null,
    val artist: String? = null,
    val thumbnail: String? = null,
    val genre: String? = null,
    val album: String? = null
)
