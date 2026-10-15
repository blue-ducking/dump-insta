package com.meta.instagram.nodoomscroll.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.meta.instagram.nodoomscroll.R
import com.meta.instagram.nodoomscroll.ui.MainActivity

/**
 * "Push new DMs" without Meta developer credentials.
 *
 * Instagram's own notification is the transport: we listen for it (user grants
 * Notification Access), filter to Direct messages, and re-post a clean alert
 * whose tap lands on OUR guarded WebView inbox - never on the Reels feed.
 */
class DmNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        if (sbn.packageName != INSTAGRAM_PACKAGE) return
        if (!looksLikeDirectMessage(sbn)) return

        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: getString(R.string.notif_dm_title)
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: getString(R.string.notif_dm_body)

        ensureChannel()
        val openInbox = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_INBOX
            putExtra(MainActivity.EXTRA_START_PATH, "/direct/")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openInbox,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val out = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID + sbn.id, out)
    }

    /**
     * Heuristic: Instagram uses different channels/tags per event type. We only
     * forward things that read like a message, so likes/follow requests stay quiet.
     */
    private fun looksLikeDirectMessage(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        val tag = sbn.tag ?: ""
        val haystack = listOfNotNull(
            n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            n.extras?.getCharSequence("android.subText")?.toString(),
            tag.lowercase(),
        ).joinToString(" ").lowercase()

        val dmSignals = listOf("message", "sent you", "replied", "direct", "dm", "tagged you in a reel")
        val noiseSignals = listOf("started following", "liked your", "new follower", "your reel")
        return dmSignals.any { haystack.contains(it) } && !noiseSignals.any { haystack.contains(it) }
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val ACTION_OPEN_INBOX = "com.meta.instagram.nodoomscroll.OPEN_INBOX"
        private const val CHANNEL_ID = "dm_v1"
        private const val NOTIF_ID = 4100
    }
}
