package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** A message waiting to be sent at a later time. The notification server sends it, even if the app is closed. */
data class ScheduledMessage(val id: String, val roomId: String, val text: String, val sendAt: Date)

class ScheduledViewModel : ViewModel() {
    private val col = FirebaseFirestore.getInstance().collection("scheduledMessages")
    private val _items = MutableStateFlow<List<ScheduledMessage>>(emptyList())
    val items: StateFlow<List<ScheduledMessage>> = _items.asStateFlow()
    private var registration: ListenerRegistration? = null
    private var uid = ""

    fun start(uid: String) {
        if (this.uid == uid) return
        this.uid = uid
        registration?.remove()
        registration = col.whereEqualTo("senderId", uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            _items.value = snapshot.documents.map {
                ScheduledMessage(it.id, it.getString("roomId").orEmpty(), it.getString("text").orEmpty(), it.getTimestamp("sendAt")?.toDate() ?: Date())
            }.sortedBy { it.sendAt }
        }
    }

    suspend fun schedule(roomId: String, text: String, senderName: String, sendAt: Date): Result<Unit> = runCatching {
        withTimeout(15_000) {
            col.add(
                mapOf(
                    "roomId" to roomId,
                    "senderId" to uid,
                    "senderName" to senderName.take(60),
                    "text" to text.take(2000),
                    "sendAt" to Timestamp(sendAt),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun cancel(item: ScheduledMessage) {
        viewModelScope.launch { runCatching { col.document(item.id).delete().await() } }
    }

    override fun onCleared() {
        registration?.remove()
    }
}
