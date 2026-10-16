package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

/**
 * A prayer request. Requests for the whole church live in the "prayers" collection; requests meant only
 * for church leaders live in "leaderPrayers".
 */
data class Prayer(
    val id: String = "",
    val text: String = "",
    val authorId: String = "",
    /** Empty when the person chose to post without their name. */
    val authorName: String = "",
    val anonymous: Boolean = false,
    val createdAt: Date? = null,
    val prayedBy: List<String> = emptyList(),
    val answered: Boolean = false,
    val leadersOnly: Boolean = false,
    val edited: Boolean = false,
    val history: List<EditVersion> = emptyList(),
    /** People who tapped "I care" and "Praise" (the first button, "I prayed", is [prayedBy]). */
    val cares: List<String> = emptyList(),
    val praise: List<String> = emptyList()
)

class PrayersRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val church = db.collection("prayers")
    private val leaders = db.collection("leaderPrayers")

    private fun observe(query: Query, leadersOnly: Boolean): Flow<Result<List<Prayer>>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
            } else if (snapshot != null) {
                trySend(Result.success(snapshot.documents.map { it.toPrayer(leadersOnly) }))
            }
        }
        awaitClose { registration.remove() }
    }

    fun observeChurch() = observe(church.orderBy("createdAt", Query.Direction.DESCENDING).limit(100), false)

    /** Admins see every leaders-only request. Everyone else sees only their own. */
    fun observeLeaders(isAdmin: Boolean, uid: String) =
        if (isAdmin) observe(leaders.orderBy("createdAt", Query.Direction.DESCENDING).limit(100), true)
        else observe(leaders.whereEqualTo("authorId", uid).limit(100), true)

    suspend fun post(text: String, uid: String, name: String, anonymous: Boolean, leadersOnly: Boolean) {
        val data = hashMapOf<String, Any>(
            "text" to text,
            "authorId" to uid,
            "anonymous" to anonymous,
            "createdAt" to FieldValue.serverTimestamp(),
            "prayedBy" to emptyList<String>(),
            "answered" to false
        )
        if (!anonymous) data["authorName"] = name
        withTimeout(15_000) { (if (leadersOnly) leaders else church).add(data).await() }
    }

    private fun collectionFor(p: Prayer) = if (p.leadersOnly) leaders else church

    suspend fun setPrayed(p: Prayer, uid: String, prayed: Boolean) {
        val change = if (prayed) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
        withTimeout(15_000) { collectionFor(p).document(p.id).update("prayedBy", change).await() }
    }

    /** "I care" ([field] = "cares") or "Praise" ([field] = "praise"): add or remove my name. */
    suspend fun setReaction(p: Prayer, uid: String, field: String, on: Boolean) {
        val change = if (on) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
        withTimeout(15_000) { collectionFor(p).document(p.id).update(field, change).await() }
    }

    suspend fun setAnswered(p: Prayer, answered: Boolean) {
        withTimeout(15_000) { collectionFor(p).document(p.id).update("answered", answered).await() }
    }

    suspend fun edit(p: Prayer, newText: String) {
        withTimeout(15_000) {
            collectionFor(p).document(p.id).update(
                mapOf<String, Any>(
                    "text" to newText,
                    "edited" to true,
                    "editedAt" to FieldValue.serverTimestamp(),
                    "history" to historyWith(p.text, p.history)
                )
            ).await()
        }
    }

    suspend fun delete(p: Prayer) {
        withTimeout(15_000) { collectionFor(p).document(p.id).delete().await() }
    }

    private fun DocumentSnapshot.toPrayer(leadersOnly: Boolean) = Prayer(
        id = id,
        text = getString("text") ?: "",
        authorId = getString("authorId") ?: "",
        authorName = getString("authorName") ?: "",
        anonymous = getBoolean("anonymous") ?: false,
        createdAt = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
        prayedBy = (get("prayedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        answered = getBoolean("answered") ?: false,
        leadersOnly = leadersOnly,
        edited = getBoolean("edited") ?: false,
        history = parseHistory(get("history")),
        cares = (get("cares") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        praise = (get("praise") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    )
}

data class PrayersState(
    val church: List<Prayer> = emptyList(),
    val leaders: List<Prayer> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false
)

class PrayersViewModel : ViewModel() {
    private val repo = PrayersRepository()

    private val _state = MutableStateFlow(PrayersState())
    val state: StateFlow<PrayersState> = _state.asStateFlow()

    private var jobs: List<Job> = emptyList()
    private var started = ""

    /** Starts watching. Safe to call repeatedly. */
    fun start(uid: String, isAdmin: Boolean) {
        val key = "$uid|$isAdmin"
        if (started == key) return
        started = key
        jobs.forEach { it.cancel() }
        _state.value = PrayersState()
        jobs = listOf(
            viewModelScope.launch {
                repo.observeChurch().collect { result ->
                    result.onSuccess { list ->
                        _state.update { s -> s.copy(church = list, loading = false, loadFailed = false) }
                    }.onFailure {
                        _state.update { s -> s.copy(loading = false, loadFailed = true) }
                    }
                }
            },
            viewModelScope.launch {
                repo.observeLeaders(isAdmin, uid).collect { result ->
                    result.onSuccess { list ->
                        val sorted = list.sortedByDescending { p -> p.createdAt?.time ?: 0L }
                        _state.update { s -> s.copy(leaders = sorted) }
                    }
                }
            }
        )
    }

    suspend fun post(text: String, uid: String, name: String, anonymous: Boolean, leadersOnly: Boolean): Result<Unit> =
        runCatching { repo.post(text, uid, name, anonymous, leadersOnly) }

    fun togglePrayed(p: Prayer, uid: String) {
        val prayed = uid !in p.prayedBy
        viewModelScope.launch { runCatching { repo.setPrayed(p, uid, prayed) } }
    }

    fun toggleReaction(p: Prayer, uid: String, field: String) {
        val list = if (field == "cares") p.cares else p.praise
        val on = uid !in list
        viewModelScope.launch { runCatching { repo.setReaction(p, uid, field, on) } }
    }

    fun setAnswered(p: Prayer, answered: Boolean) {
        viewModelScope.launch { runCatching { repo.setAnswered(p, answered) } }
    }

    fun edit(p: Prayer, newText: String) {
        viewModelScope.launch { runCatching { repo.edit(p, newText) } }
    }

    fun delete(p: Prayer) {
        viewModelScope.launch { runCatching { repo.delete(p) } }
    }
}
