package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
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

/** A job people can sign up for, for example "Welcome team, Sunday 10:30, 3 people needed". */
data class ServingSlot(
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val start: Date = Date(),
    val needed: Int = 1,
    val volunteers: List<String> = emptyList(),
    /** uid -> name of the people who signed up. */
    val volunteerNames: Map<String, String> = emptyMap(),
    val createdBy: String = ""
) {
    val full: Boolean get() = volunteers.size >= needed
}

class ServingRepository(db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val col = db.collection("servingSlots")

    /** Slots from the start of today onward, soonest first. */
    fun observeUpcoming(from: Date): Flow<Result<List<ServingSlot>>> = callbackFlow {
        val registration = col.whereGreaterThanOrEqualTo("start", Timestamp(from)).orderBy("start").limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(Result.success(snapshot.documents.map { it.toSlot() }))
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun save(slot: ServingSlot, authorUid: String) {
        val data = hashMapOf<String, Any>(
            "title" to slot.title,
            "notes" to slot.notes,
            "start" to Timestamp(slot.start),
            "needed" to slot.needed,
            "volunteers" to slot.volunteers,
            "volunteerNames" to slot.volunteerNames,
            "createdBy" to slot.createdBy.ifEmpty { authorUid },
            "createdAt" to Timestamp.now()
        )
        withTimeout(15_000) {
            if (slot.id.isEmpty()) col.add(data).await() else col.document(slot.id).set(data).await()
        }
    }

    suspend fun delete(slot: ServingSlot) {
        withTimeout(15_000) { col.document(slot.id).delete().await() }
    }

    suspend fun signUp(slot: ServingSlot, uid: String, name: String) {
        withTimeout(15_000) {
            col.document(slot.id).update(
                mapOf("volunteers" to FieldValue.arrayUnion(uid), "volunteerNames.$uid" to name.take(60))
            ).await()
        }
    }

    suspend fun withdraw(slot: ServingSlot, uid: String) {
        withTimeout(15_000) {
            col.document(slot.id).update(
                mapOf("volunteers" to FieldValue.arrayRemove(uid), "volunteerNames.$uid" to FieldValue.delete())
            ).await()
        }
    }

    private fun DocumentSnapshot.toSlot() = ServingSlot(
        id = id,
        title = getString("title") ?: "",
        notes = getString("notes") ?: "",
        start = getTimestamp("start")?.toDate() ?: Date(),
        needed = (getLong("needed") ?: 1L).toInt(),
        volunteers = (get("volunteers") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        volunteerNames = (get("volunteerNames") as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap(),
        createdBy = getString("createdBy") ?: ""
    )
}

data class ServingState(
    val slots: List<ServingSlot> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val actionFailed: Boolean = false
)

class ServingViewModel : ViewModel() {
    private val repo = ServingRepository()

    private val _state = MutableStateFlow(ServingState())
    val state: StateFlow<ServingState> = _state.asStateFlow()

    init {
        val startOfToday = java.time.LocalDate.now().toStartOfDayDate()
        viewModelScope.launch {
            repo.observeUpcoming(startOfToday).collect { result ->
                result.onSuccess { list ->
                    _state.update { it.copy(slots = list, loading = false, loadFailed = false) }
                }.onFailure {
                    _state.update { it.copy(loading = false, loadFailed = true) }
                }
            }
        }
    }

    suspend fun save(slot: ServingSlot, authorUid: String): Result<Unit> = runCatching { repo.save(slot, authorUid) }

    fun delete(slot: ServingSlot) {
        viewModelScope.launch { runCatching { repo.delete(slot) } }
    }

    fun toggleSignUp(slot: ServingSlot, uid: String, name: String) {
        viewModelScope.launch {
            val result = runCatching {
                if (uid in slot.volunteers) repo.withdraw(slot, uid) else repo.signUp(slot, uid, name)
            }
            _state.update { it.copy(actionFailed = result.isFailure) }
        }
    }

    fun clearActionFailed() = _state.update { it.copy(actionFailed = false) }
}
