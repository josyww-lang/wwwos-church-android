package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Whether the church is streaming right now (settings/live). Set by an admin, or by the notification server. */
data class LiveInfo(
    val live: Boolean = false,
    val url: String = "",
    val title: String = "",
    val source: String = "",
    val updatedAt: Date? = null
) {
    /** A banner that nobody has refreshed for 6 hours is treated as forgotten and hidden. */
    fun isOn(now: Long = System.currentTimeMillis()): Boolean =
        live && url.startsWith("https://") && (updatedAt == null || now - updatedAt.time < 6 * 60 * 60 * 1000L)
}

class LiveViewModel : ViewModel() {
    private val doc = FirebaseFirestore.getInstance().collection("settings").document("live")
    private val _info = MutableStateFlow(LiveInfo())
    val info: StateFlow<LiveInfo> = _info.asStateFlow()
    private var registration: ListenerRegistration? = null

    init {
        registration = doc.addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null) {
                _info.value = LiveInfo(
                    live = snapshot.getBoolean("live") ?: false,
                    url = snapshot.getString("url") ?: "",
                    title = snapshot.getString("title") ?: "",
                    source = snapshot.getString("source") ?: "",
                    updatedAt = snapshot.getTimestamp("updatedAt", ServerTimestampBehavior.ESTIMATE)?.toDate()
                )
            }
        }
    }

    override fun onCleared() {
        registration?.remove()
    }

    suspend fun save(live: Boolean, url: String, title: String): Result<Unit> = runCatching {
        withTimeout(15_000) {
            doc.set(
                mapOf(
                    "live" to live,
                    "url" to url.trim(),
                    "title" to title.trim().take(100),
                    "source" to "manual",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }
}
