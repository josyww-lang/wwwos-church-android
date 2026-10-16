package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** A private message from a member to the church leaders. Only admins can read these. */
data class FeedbackMessage(
    val id: String = "",
    val text: String = "",
    /** idea, concern, praise or other */
    val category: String = "other",
    val anonymous: Boolean = false,
    /** Empty when it was sent anonymously. */
    val authorName: String = "",
    val createdAt: Date? = null,
    /** "new" or "done" */
    val status: String = "new"
)

val FEEDBACK_CATEGORIES = listOf("idea", "concern", "praise", "other")

class FeedbackRepository(db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val col = db.collection("feedback")

    /** Admins only. */
    fun observeInbox(): Flow<Result<List<FeedbackMessage>>> = callbackFlow {
        val registration = col.orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(
                        Result.success(
                            snapshot.documents.map { d ->
                                FeedbackMessage(
                                    id = d.id,
                                    text = d.getString("text") ?: "",
                                    category = d.getString("category") ?: "other",
                                    anonymous = d.getBoolean("anonymous") ?: false,
                                    authorName = d.getString("authorName") ?: "",
                                    createdAt = d.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
                                    status = d.getString("status") ?: "new"
                                )
                            }
                        )
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    /** An anonymous message carries no information about who sent it. */
    suspend fun send(text: String, category: String, anonymous: Boolean, uid: String, name: String) {
        val data = hashMapOf<String, Any>(
            "text" to text,
            "category" to category,
            "anonymous" to anonymous,
            "createdAt" to FieldValue.serverTimestamp(),
            "status" to "new"
        )
        if (!anonymous) {
            data["authorId"] = uid
            data["authorName"] = name.take(60)
        }
        withTimeout(15_000) { col.add(data).await() }
    }

    suspend fun setStatus(id: String, status: String) {
        withTimeout(15_000) { col.document(id).update("status", status).await() }
    }

    suspend fun delete(id: String) {
        withTimeout(15_000) { col.document(id).delete().await() }
    }
}

data class FeedbackState(
    val inbox: List<FeedbackMessage> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false
)

class FeedbackViewModel : ViewModel() {
    private val repo = FeedbackRepository()

    private val _state = MutableStateFlow(FeedbackState())
    val state: StateFlow<FeedbackState> = _state.asStateFlow()

    private var inboxJob: Job? = null

    /** Admins load the inbox. Other members have nothing to load. */
    fun start(isAdmin: Boolean) {
        if (!isAdmin || inboxJob != null) return
        inboxJob = viewModelScope.launch {
            repo.observeInbox().collect { result ->
                result.onSuccess { list ->
                    _state.update { it.copy(inbox = list, loading = false, loadFailed = false) }
                }.onFailure {
                    _state.update { it.copy(loading = false, loadFailed = true) }
                }
            }
        }
    }

    suspend fun send(text: String, category: String, anonymous: Boolean, uid: String, name: String): Result<Unit> =
        runCatching { repo.send(text, category, anonymous, uid, name) }

    fun toggleDone(m: FeedbackMessage) {
        viewModelScope.launch { runCatching { repo.setStatus(m.id, if (m.status == "done") "new" else "done") } }
    }

    fun delete(m: FeedbackMessage) {
        viewModelScope.launch { runCatching { repo.delete(m.id) } }
    }
}
