package org.yourchurch.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.TimeZone
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Which notifications this person wants, and their quiet hours at night.
 * Kept on the phone, and copied to the notification server (with the phone's time zone) so it can respect them.
 */
object NotificationPrefs {
    private const val FILE = "settings"

    var chat by mutableStateOf(true)
        private set
    var announcements by mutableStateOf(true)
        private set
    var events by mutableStateOf(true)
        private set
    var live by mutableStateOf(true)
        private set
    var sermons by mutableStateOf(true)
        private set
    var giving by mutableStateOf(true)
        private set
    var reminders by mutableStateOf(true)
        private set
    var quietOn by mutableStateOf(false)
        private set
    /** Quiet hours, as whole hours of the day (0 to 23). */
    var quietFrom by mutableIntStateOf(22)
        private set
    var quietTo by mutableIntStateOf(7)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        chat = p.getBoolean("np_chat", true)
        announcements = p.getBoolean("np_announcements", true)
        events = p.getBoolean("np_events", true)
        live = p.getBoolean("np_live", true)
        sermons = p.getBoolean("np_sermons", true)
        giving = p.getBoolean("np_giving", true)
        reminders = p.getBoolean("np_reminders", true)
        quietOn = p.getBoolean("np_quiet_on", false)
        quietFrom = p.getInt("np_quiet_from", 22).coerceIn(0, 23)
        quietTo = p.getInt("np_quiet_to", 7).coerceIn(0, 23)
        loaded = true
    }

    fun save(
        context: Context,
        chat: Boolean,
        announcements: Boolean,
        events: Boolean,
        live: Boolean,
        sermons: Boolean,
        giving: Boolean,
        reminders: Boolean,
        quietOn: Boolean,
        quietFrom: Int,
        quietTo: Int
    ) {
        this.chat = chat
        this.announcements = announcements
        this.events = events
        this.live = live
        this.sermons = sermons
        this.giving = giving
        this.reminders = reminders
        this.quietOn = quietOn
        this.quietFrom = quietFrom.coerceIn(0, 23)
        this.quietTo = quietTo.coerceIn(0, 23)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("np_chat", chat).putBoolean("np_announcements", announcements)
            .putBoolean("np_events", events).putBoolean("np_live", live)
            .putBoolean("np_sermons", sermons).putBoolean("np_giving", giving).putBoolean("np_reminders", reminders)
            .putBoolean("np_quiet_on", quietOn).putInt("np_quiet_from", this.quietFrom).putInt("np_quiet_to", this.quietTo)
            .apply()
    }

    /** Tells the notification server these choices, and which time zone this phone is in. */
    suspend fun sync(uid: String) {
        val prefs = mapOf(
            "chat" to chat,
            "announcements" to announcements,
            "events" to events,
            "live" to live,
            "sermons" to sermons,
            "giving" to giving,
            "eventReminders" to reminders,
            "quietOn" to quietOn,
            "quietFrom" to quietFrom,
            "quietTo" to quietTo
        )
        val offsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        withTimeout(15_000) {
            FirebaseFirestore.getInstance().collection("fcmTokens").document(uid)
                .set(mapOf("prefs" to prefs, "tzOffset" to offsetMinutes), SetOptions.merge())
                .await()
        }
    }
}
