package com.unshoo.pixelmusic.data.remote.youtube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.unshoo.pixelmusic.data.model.youtube.Playlist
import com.unshoo.pixelmusic.data.model.youtube.Song
import kotlin.math.abs

/**
 * Spotify-style download notification manager.
 *
 * Progress notifications:
 *  - Title   : song/playlist name (truncated to 40 chars)
 *  - Text    : "3 of 12 · 25%"  or  "25%"  for single songs
 *  - Sub-text: "PixelMusic · Downloading"
 *  - Progress: live deterministic progress bar (setOnlyAlertOnce = no repeated sound/vibration)
 *  - Actions : [Cancel] button wired to WorkManager via DownloadCancelReceiver broadcast
 *
 * Completion / failure:
 *  - Auto-cancel on tap, no action buttons — clean and minimal like Spotify
 */
object PixelMusicNotificationManager {

    private lateinit var notificationManager: NotificationManager
    private lateinit var openAppIntent: PendingIntent

    // ─────────────────────────────── Init ─────────────────────────────────────

    fun init(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        openAppIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Channel.entries.forEach { ch ->
                val nc = NotificationChannel(ch.channelId, ch.channelName, ch.importance).apply {
                    description = ch.description
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(nc)
            }
        }
    }

    // ───────────────────── Playlist download progress ─────────────────────────

    /**
     * Call each time a song in the playlist finishes.
     * [workId] is the UUID string of the WorkManager task — powers the Cancel button.
     */
    fun showPlaylistDownloadProgress(
        context: Context,
        playlist: Playlist,
        currentSong: Int,
        totalSongs: Int,
        workId: String? = null
    ) {
        ensureInit(context)
        val percent = if (totalSongs > 0)
            ((currentSong.toFloat() / totalSongs) * 100).toInt().coerceIn(0, 100)
        else 0
        val notifId = getNotifId(playlist.info.id)

        val builder = baseBuilder(context, Channel.PLAYLIST_DOWNLOAD)
            .setContentTitle(playlist.info.title.truncate(40))
            .setContentText("$currentSong of $totalSongs · $percent%")
            .setSubText("Downloading")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(totalSongs, currentSong, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(Channel.PLAYLIST_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (workId != null) builder.addAction(cancelAction(context, workId, notifId))

        val notification = builder.build()
        com.unshoo.pixelmusic.data.service.LiveUpdateUtil.promote(notification)
        notificationManager.notify(notifId, notification)
        updateGroupSummary(context)
    }

    fun showPlaylistDownloadSuccess(context: Context, playlist: Playlist) {
        ensureInit(context)
        val id = getNotifId(playlist.info.id)
        notificationManager.cancel(id)
        val n = baseBuilder(context, Channel.PLAYLIST_DOWNLOAD)
            .setContentTitle(playlist.info.title.truncate(40))
            .setContentText("Saved offline")
            .setSubText("PixelMusic")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setGroup(Channel.PLAYLIST_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(id, n)
        updateGroupSummary(context)
    }

    fun showPlaylistDownloadFailure(context: Context, playlist: Playlist) {
        ensureInit(context)
        val id = getNotifId(playlist.info.id)
        notificationManager.cancel(id)
        val n = baseBuilder(context, Channel.PLAYLIST_DOWNLOAD)
            .setContentTitle(playlist.info.title.truncate(40))
            .setContentText("Download failed")
            .setSubText("PixelMusic")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setGroup(Channel.PLAYLIST_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(id, n)
        updateGroupSummary(context)
    }

    fun showPlaylistDownloadCanceled(context: Context, playlist: Playlist) {
        cancelPlaylistDownloadNotification(context, playlist.info.id)
    }

    fun cancelPlaylistDownloadNotification(context: Context, playlistId: String) {
        ensureInit(context)
        runCatching { notificationManager.cancel(getNotifId(playlistId)) }
        runCatching { notificationManager.cancel(GROUP_SUMMARY_ID) }
    }

    fun cancelAllDownloadNotifications(context: Context) {
        ensureInit(context)
        runCatching { notificationManager.cancel(GROUP_SUMMARY_ID) }
    }

    // ─────────────────────── Single-song download ──────────────────────────────

    /**
     * Shows a single-song progress notification with a live percentage.
     * Pass percent=0 initially for an indeterminate spinner until Content-Length is known.
     */
    fun showSongDownloadProgress(
        context: Context,
        song: Song,
        percent: Int,
        workId: String? = null
    ) {
        ensureInit(context)
        val notifId = getNotifId(song.youtubeId)
        val indeterminate = percent <= 0

        val builder = baseBuilder(context, Channel.SONG_DOWNLOAD)
            .setContentTitle(song.title.truncate(40))
            .setContentText(if (indeterminate) "Starting…" else "$percent%")
            .setSubText("Downloading")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(Channel.SONG_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (workId != null) builder.addAction(cancelAction(context, workId, notifId))

        val notification = builder.build()
        com.unshoo.pixelmusic.data.service.LiveUpdateUtil.promote(notification)
        notificationManager.notify(notifId, notification)
    }

    fun showSongDownloadSuccess(context: Context, song: Song) {
        ensureInit(context)
        val n = baseBuilder(context, Channel.SONG_DOWNLOAD)
            .setContentTitle(song.title.truncate(40))
            .setContentText("Saved offline")
            .setSubText(song.artist.truncate(30))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setGroup(Channel.SONG_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(getNotifId(song.youtubeId), n)
    }

    fun showSongDownloadFailed(context: Context, song: Song) {
        ensureInit(context)
        val n = baseBuilder(context, Channel.SONG_DOWNLOAD)
            .setContentTitle(song.title.truncate(40))
            .setContentText("Download failed")
            .setSubText(song.artist.truncate(30))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setGroup(Channel.SONG_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(getNotifId(song.youtubeId), n)
    }

    // ────────────────────────── Private helpers ────────────────────────────────

    private fun ensureInit(context: Context) {
        if (!::notificationManager.isInitialized) init(context)
    }

    private fun baseBuilder(context: Context, channel: Channel) =
        NotificationCompat.Builder(context, channel.channelId)
            .setContentIntent(openAppIntent)

    /**
     * Builds a "Cancel" action PendingIntent that fires [DownloadCancelReceiver]
     * to cancel the WorkManager job and auto-dismiss this notification.
     */
    private fun cancelAction(
        context: Context,
        workId: String,
        notifId: Int
    ): NotificationCompat.Action {
        val intent = Intent(context, DownloadCancelReceiver::class.java).apply {
            action = DownloadCancelReceiver.ACTION_CANCEL
            putExtra(DownloadCancelReceiver.EXTRA_WORK_ID, workId)
            putExtra(DownloadCancelReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val pi = PendingIntent.getBroadcast(
            context, notifId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            pi
        )
    }

    private fun updateGroupSummary(context: Context) {
        val summary = baseBuilder(context, Channel.PLAYLIST_DOWNLOAD)
            .setContentTitle("PixelMusic Downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setGroup(Channel.PLAYLIST_DOWNLOAD.group)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(GROUP_SUMMARY_ID, summary)
    }

    private fun getNotifId(id: String): Int = 1000 + abs(id.hashCode() and 0x7fffffff)

    private fun String.truncate(max: Int) = if (length > max) take(max - 1) + "…" else this

    private const val GROUP_SUMMARY_ID = 0

    // ────────────────────────── Notification channels ─────────────────────────

    private enum class Channel(
        val channelId: String,
        val channelName: String,
        val description: String,
        val importance: Int,
        val group: String
    ) {
        PLAYLIST_DOWNLOAD(
            channelId = "playlist_progress",
            channelName = "Playlist Downloads",
            description = "Live progress for playlist downloads",
            importance = NotificationManager.IMPORTANCE_LOW,
            group = "PLAYLIST_GROUP"
        ),
        SONG_DOWNLOAD(
            channelId = "song_alerts",
            channelName = "Song Downloads",
            description = "Individual song download status",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            group = "SONG_GROUP"
        )
    }
}
