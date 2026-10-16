package org.yourchurch.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Push notifications.
 *
 * Each signed-in person is subscribed to two topics:
 *   "all"        - messages for everyone
 *   "lang_xx"    - messages for people using the app in that language (lang_en, lang_nb, lang_om)
 * Admins send messages to a topic from the Firebase console.
 */
object Notifications {
    const val CHANNEL_ID = "announcements"
    const val CHAT_CHANNEL_ID = "chat_messages"
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "notifications_enabled"
    private const val KEY_ASKED = "notification_permission_asked"
    private const val KEY_TOPICS = "notification_topics"
    private val lock = Any()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun askedForPermission(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    fun markAskedForPermission(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }

    private fun languageTopic(context: Context): String = when (LocaleManager.languageCode(context)) {
        "nb", "nn", "no" -> "nb"
        "om" -> "om"
        else -> "en"
    }

    /**
     * Makes the topic subscriptions match the current situation. Safe to call as often as you like:
     * it only changes what differs, and retries on the next call if something failed (for example offline).
     */
    fun sync(context: Context, signedIn: Boolean) {
        val app = context.applicationContext
        val wanted: Set<String> =
            if (signedIn && isEnabled(app)) setOf("all", "lang_" + languageTopic(app)) else emptySet()
        val current = stored(app)
        val messaging = FirebaseMessaging.getInstance()

        (current - wanted).forEach { topic ->
            messaging.unsubscribeFromTopic(topic).addOnSuccessListener { storeRemove(app, topic) }
        }
        (wanted - current).forEach { topic ->
            messaging.subscribeToTopic(topic).addOnSuccessListener { storeAdd(app, topic) }
        }
    }

    private fun stored(app: Context): Set<String> =
        HashSet(prefs(app).getStringSet(KEY_TOPICS, emptySet()) ?: emptySet())

    private fun storeAdd(app: Context, topic: String) = synchronized(lock) {
        val topics = HashSet(stored(app))
        topics.add(topic)
        prefs(app).edit().putStringSet(KEY_TOPICS, topics).apply()
    }

    private fun storeRemove(app: Context, topic: String) = synchronized(lock) {
        val topics = HashSet(stored(app))
        topics.remove(topic)
        prefs(app).edit().putStringSet(KEY_TOPICS, topics).apply()
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.title_announcements),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
        // Chat messages get their own channel, so people can control them separately in phone settings.
        manager.createNotificationChannel(
            NotificationChannel(
                CHAT_CHANNEL_ID,
                context.getString(R.string.chat_title),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    /** Shows a notification ourselves. Used when a message arrives while the app is open. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String?, body: String?, roomId: String? = null, target: String? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (roomId != null) putExtra("roomId", roomId)
            if (target != null) putExtra("target", target)
        }
        val pending = PendingIntent.getActivity(
            context, (roomId ?: target)?.hashCode() ?: 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, if (roomId != null) CHAT_CHANNEL_ID else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setContentTitle(title ?: context.getString(R.string.app_name))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .apply {
                if (roomId != null) {
                    setPriority(NotificationCompat.PRIORITY_HIGH)
                    setCategory(NotificationCompat.CATEGORY_MESSAGE)
                }
            }
            .build()
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }
}
