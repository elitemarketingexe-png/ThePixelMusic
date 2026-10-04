package com.unshoo.pixelmusic.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.unshoo.pixelmusic.MainActivity
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.update.AppReleaseAsset
import com.unshoo.pixelmusic.data.model.update.AppReleaseInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "pixelmusic_app_updates"
        const val NOTIFICATION_ID = 2001

        const val ACTION_SHOW_UPDATE_DIALOG = "com.unshoo.pixelmusic.action.SHOW_UPDATE_DIALOG"
        const val EXTRA_UPDATE_TAG = "extra_update_tag"
        const val EXTRA_UPDATE_TITLE = "extra_update_title"
        const val EXTRA_UPDATE_BODY = "extra_update_body"
        const val EXTRA_UPDATE_HTML_URL = "extra_update_html_url"
        const val EXTRA_UPDATE_DOWNLOAD_URL = "extra_update_download_url"
        const val EXTRA_UPDATE_ASSET_NAME = "extra_update_asset_name"
        const val EXTRA_UPDATE_ASSET_SIZE = "extra_update_asset_size"
        const val EXTRA_UPDATE_ASSET_ARCH = "extra_update_asset_arch"
        const val EXTRA_AUTO_DOWNLOAD = "extra_auto_download"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_name) + " Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for newly released PixelMusic updates and changelogs"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Posts a notification informing the user that a new release is available.
     */
    fun showUpdateNotification(release: AppReleaseInfo, targetAsset: AppReleaseAsset?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                Timber.w("showUpdateNotification: POST_NOTIFICATIONS permission not granted. Skipping notification.")
                return
            }
        }

        // Tap intent to open in-app update sheet
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_SHOW_UPDATE_DIALOG
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_UPDATE_TAG, release.tagName)
            putExtra(EXTRA_UPDATE_TITLE, release.name ?: release.tagName)
            putExtra(EXTRA_UPDATE_BODY, release.body)
            putExtra(EXTRA_UPDATE_HTML_URL, release.htmlUrl)
            putExtra(EXTRA_UPDATE_DOWNLOAD_URL, targetAsset?.downloadUrl ?: release.htmlUrl)
            putExtra(EXTRA_UPDATE_ASSET_NAME, targetAsset?.name ?: "")
            putExtra(EXTRA_UPDATE_ASSET_SIZE, targetAsset?.sizeBytes ?: 0L)
            putExtra(EXTRA_UPDATE_ASSET_ARCH, targetAsset?.architecture ?: "")
        }

        val pendingContentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: View GitHub repo
        val gitHubIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/ianshulyadav/PixelMusicApp")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingGitHubIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
            gitHubIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.pixelmusic_base_monochrome)
            .setColor(ContextCompat.getColor(context, R.color.my_primary))
            .setContentTitle("PixelMusic ${release.tagName}")
            .setContentText("New update available")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("A new version is available. Tap to view or download what's new.")
            )       
            .setContentIntent(pendingContentIntent)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.rounded_touch_app_24,
                "Update",
                pendingContentIntent
            )
            .addAction(
                R.drawable.github,
                "GitHub",
                pendingGitHubIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun dismissNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
