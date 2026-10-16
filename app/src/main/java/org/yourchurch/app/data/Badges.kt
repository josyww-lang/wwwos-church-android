package org.yourchurch.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** When this phone last opened each section ("news", "events", "sermons", "serve", "pray"). */
object SectionSeen {
    private const val FILE = "settings"

    /** Changes whenever a section is marked as seen, so the numbers on the buttons refresh. */
    var version by mutableIntStateOf(0)
        private set

    /** The first time, everything that already exists counts as seen. */
    fun lastSeen(context: Context, key: String): Long {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val saved = prefs.getLong("seen_$key", -1L)
        if (saved >= 0) return saved
        val now = System.currentTimeMillis()
        prefs.edit().putLong("seen_$key", now).apply()
        return now
    }

    fun markSeen(context: Context, key: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong("seen_$key", System.currentTimeMillis()).apply()
        version++
    }
}

class NewItem(val createdAt: Long, val authorId: String)

/**
 * Watches the newest few things in each Church section (when they were created and by whom), so the buttons can show
 * how many are new since this person last opened that section, without counting what they posted themselves.
 */
class BadgesViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val _state = MutableStateFlow<Map<String, List<NewItem>>>(emptyMap())
    val state: StateFlow<Map<String, List<NewItem>>> = _state.asStateFlow()
    private val registrations = mutableListOf<ListenerRegistration>()

    init {
        watch("news", db.collection("announcements"), "authorId") { doc ->
            doc.getTimestamp("publishAt")?.toDate()?.after(Date()) != true   // not those still waiting for their time
        }
        watch("events", db.collection("events"), "createdBy")
        watch("sermons", db.collection("sermons"), "authorId") { doc -> doc.getBoolean("published") != false }
        watch("serve", db.collection("servingSlots"), "")
        watch("pray", db.collection("prayers"), "authorId")
    }

    private fun watch(key: String, collection: CollectionReference, authorField: String, keep: (DocumentSnapshot) -> Boolean = { true }) {
        registrations += collection.orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.filter(keep).mapNotNull { doc ->
                    val at = doc.getTimestamp("createdAt")?.toDate()?.time ?: return@mapNotNull null
                    NewItem(at, if (authorField.isEmpty()) "" else doc.getString(authorField).orEmpty())
                }
                _state.update { it + (key to items) }
            }
    }

    override fun onCleared() {
        registrations.forEach { it.remove() }
    }
}
