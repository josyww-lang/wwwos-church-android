package org.yourchurch.app.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SermonsState(
    val sermons: List<Sermon> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    // comments on the sermon that is open
    val comments: List<SermonComment> = emptyList(),
    val commentsLoading: Boolean = false,
    /** True if the database refused (for example the person is not approved yet). */
    val commentsDenied: Boolean = false
)

class SermonsViewModel : ViewModel() {
    private val repo = SermonRepository()

    private val _state = MutableStateFlow(SermonsState())
    val state: StateFlow<SermonsState> = _state.asStateFlow()

    private var commentsJob: Job? = null
    private var commentsFor: String? = null

    init {
        viewModelScope.launch {
            repo.observe().collect { result ->
                result.onSuccess { list ->
                    _state.update { s -> s.copy(sermons = list, loading = false, loadFailed = false) }
                }.onFailure {
                    _state.update { s -> s.copy(loading = false, loadFailed = true) }
                }
            }
        }
    }

    // ---------- Comments on the open sermon ----------

    fun openComments(sermonId: String) {
        if (commentsFor == sermonId) return
        commentsFor = sermonId
        commentsJob?.cancel()
        _state.update { it.copy(comments = emptyList(), commentsLoading = true, commentsDenied = false) }
        commentsJob = viewModelScope.launch {
            repo.observeComments(sermonId).collect { result ->
                result.onSuccess { list ->
                    _state.update { it.copy(comments = list, commentsLoading = false, commentsDenied = false) }
                }.onFailure {
                    _state.update { it.copy(commentsLoading = false, commentsDenied = true) }
                }
            }
        }
    }

    fun closeComments() {
        commentsJob?.cancel()
        commentsFor = null
        _state.update { it.copy(comments = emptyList(), commentsLoading = false, commentsDenied = false) }
    }

    fun postComment(sermonId: String, text: String, uid: String, name: String, reply: SermonComment?) {
        viewModelScope.launch { runCatching { repo.addComment(sermonId, text, uid, name, reply) } }
    }

    fun toggleLike(sermonId: String, comment: SermonComment, uid: String) {
        val had = uid in comment.likes
        viewModelScope.launch { runCatching { repo.setCommentLike(sermonId, comment.id, uid, add = !had) } }
    }

    fun deleteComment(sermonId: String, comment: SermonComment) {
        viewModelScope.launch { runCatching { repo.deleteComment(sermonId, comment.id) } }
    }

    /** Tap a reaction on a sermon: add it, or take it back if I had already chosen it. */
    fun react(sermon: Sermon, emoji: String, uid: String) {
        val had = uid in (sermon.reactions[emoji] ?: emptyList())
        viewModelScope.launch { runCatching { repo.setReaction(sermon.id, emoji, uid, add = !had) } }
    }

    // ---------- Admin actions (the Firestore and Storage rules enforce that only admins can do these) ----------

    suspend fun save(s: Sermon): Result<Unit> = runCatching { repo.save(s) }

    fun delete(s: Sermon) {
        viewModelScope.launch { runCatching { repo.delete(s) } }
    }

    fun deleteAudio(path: String) {
        viewModelScope.launch { repo.deleteAudio(path) }
    }

    fun upload(uri: Uri, mime: String): Flow<UploadEvent> = repo.upload(uri, mime)
}
