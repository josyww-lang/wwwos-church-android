package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** One church event (a document in the Firestore "events" collection). */
data class Event(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val start: Date = Date(),
    val createdBy: String = "",
    val createdAt: Date = Date()
)

/** One person's answer to "Will you come?": status is "going", "maybe" or "no". */
data class Rsvp(val uid: String, val name: String, val status: String)

class EventsRepository(db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val col = db.collection("events")

    /** Events from 12 hours ago onwards, soonest first. */
    fun observeUpcoming(): Flow<Result<List<Event>>> = callbackFlow {
        val cutoff = Timestamp(Date(System.currentTimeMillis() - 12L * 60 * 60 * 1000))
        val registration = col.whereGreaterThanOrEqualTo("start", cutoff).orderBy("start").limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(Result.success(snapshot.documents.map { it.toEvent() }))
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeRsvps(eventId: String): Flow<Result<List<Rsvp>>> = callbackFlow {
        val registration = col.document(eventId).collection("rsvps")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(
                        Result.success(
                            snapshot.documents.map {
                                Rsvp(it.id, it.getString("name") ?: "", it.getString("status") ?: "going")
                            }
                        )
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun save(e: Event) {
        val data = hashMapOf<String, Any>(
            "title" to e.title,
            "description" to e.description,
            "location" to e.location,
            "start" to Timestamp(e.start),
            "createdBy" to e.createdBy,
            "createdAt" to Timestamp(e.createdAt)
        )
        withTimeout(15_000) {
            if (e.id.isEmpty()) col.add(data).await() else col.document(e.id).set(data).await()
        }
    }

    suspend fun delete(id: String) {
        withTimeout(15_000) { col.document(id).delete().await() }
    }

    suspend fun setRsvp(eventId: String, uid: String, name: String, status: String) {
        withTimeout(15_000) {
            col.document(eventId).collection("rsvps").document(uid)
                .set(mapOf("status" to status, "name" to name, "at" to FieldValue.serverTimestamp())).await()
        }
    }

    suspend fun clearRsvp(eventId: String, uid: String) {
        withTimeout(15_000) { col.document(eventId).collection("rsvps").document(uid).delete().await() }
    }

    private fun DocumentSnapshot.toEvent() = Event(
        id = id,
        title = getString("title") ?: "",
        description = getString("description") ?: "",
        location = getString("location") ?: "",
        start = getTimestamp("start")?.toDate() ?: Date(),
        createdBy = getString("createdBy") ?: "",
        createdAt = getTimestamp("createdAt")?.toDate() ?: Date()
    )
}

data class EventsState(
    val events: List<Event> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    /** Answers for the event that is open. */
    val rsvps: List<Rsvp> = emptyList()
)

class EventsViewModel : ViewModel() {
    private val repo = EventsRepository()

    private val _state = MutableStateFlow(EventsState())
    val state: StateFlow<EventsState> = _state.asStateFlow()

    private var rsvpJob: Job? = null
    private var rsvpEventId: String? = null

    init {
        viewModelScope.launch {
            repo.observeUpcoming().collect { result ->
                result.onSuccess { list ->
                    _state.update { s -> s.copy(events = list, loading = false, loadFailed = false) }
                }.onFailure {
                    _state.update { s -> s.copy(loading = false, loadFailed = true) }
                }
            }
        }
    }

    /** Start (or stop, with null) watching the answers for one event. */
    fun watchRsvps(eventId: String?) {
        if (rsvpEventId == eventId) return
        rsvpEventId = eventId
        rsvpJob?.cancel()
        _state.update { it.copy(rsvps = emptyList()) }
        if (eventId == null) return
        rsvpJob = viewModelScope.launch {
            repo.observeRsvps(eventId).collect { result ->
                result.onSuccess { list -> _state.update { it.copy(rsvps = list) } }
            }
        }
    }

    // Admin actions. The database rules enforce that only admins can do these.
    suspend fun save(e: Event): Result<Unit> = runCatching { repo.save(e) }

    fun delete(e: Event) {
        viewModelScope.launch { runCatching { repo.delete(e.id) } }
    }

    fun setRsvp(eventId: String, uid: String, name: String, status: String) {
        viewModelScope.launch { runCatching { repo.setRsvp(eventId, uid, name, status) } }
    }

    fun clearRsvp(eventId: String, uid: String) {
        viewModelScope.launch { runCatching { repo.clearRsvp(eventId, uid) } }
    }
}
