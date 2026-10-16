package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

enum class SearchKind { Announcement, Event, Sermon, Lesson }

/** Something that can be found: [extra] is the speaker, place or passage; [body] is the longer text. */
class SearchItem(val kind: SearchKind, val id: String, val title: String, val extra: String, val body: String, val date: Date?)

data class SearchState(val loading: Boolean = false, val failed: Boolean = false, val items: List<SearchItem> = emptyList())

/** Loads the church's announcements, events, sermons and lessons once, then searching them is instant. */
class SearchViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var loadedAt = 0L

    fun load(isAdmin: Boolean) {
        if (System.currentTimeMillis() - loadedAt < 5 * 60_000L && _state.value.items.isNotEmpty()) return
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val items = mutableListOf<SearchItem>()
            var failures = 0
            val now = System.currentTimeMillis()

            runCatching {
                withTimeout(20_000) { db.collection("announcements").orderBy("date", Query.Direction.DESCENDING).limit(200).get().await() }
            }.onSuccess { snap ->
                snap.documents.forEach {
                    val publishAt = it.getTimestamp("publishAt")?.toDate()
                    if (!isAdmin && publishAt != null && publishAt.time > now) return@forEach   // still waiting for its time
                    items += SearchItem(SearchKind.Announcement, it.id, it.getString("title").orEmpty(), "", it.getString("message").orEmpty(), it.getTimestamp("date")?.toDate())
                }
            }.onFailure { failures++ }

            runCatching {
                withTimeout(20_000) { db.collection("events").orderBy("start", Query.Direction.DESCENDING).limit(200).get().await() }
            }.onSuccess { snap ->
                snap.documents.forEach {
                    items += SearchItem(SearchKind.Event, it.id, it.getString("title").orEmpty(), it.getString("location").orEmpty(), it.getString("description").orEmpty(), it.getTimestamp("start")?.toDate())
                }
            }.onFailure { failures++ }

            runCatching {
                withTimeout(20_000) { db.collection("sermons").orderBy("date", Query.Direction.DESCENDING).limit(200).get().await() }
            }.onSuccess { snap ->
                snap.documents.forEach {
                    if (!isAdmin && it.getBoolean("published") == false) return@forEach
                    val extra = listOf(it.getString("speaker").orEmpty(), it.getString("series").orEmpty()).filter { s -> s.isNotBlank() }.joinToString(" \u00B7 ")
                    items += SearchItem(SearchKind.Sermon, it.id, it.getString("title").orEmpty(), extra, it.getString("description").orEmpty(), it.getTimestamp("date")?.toDate())
                }
            }.onFailure { failures++ }

            runCatching {
                withTimeout(20_000) { db.collection("lessons").orderBy("createdAt", Query.Direction.DESCENDING).limit(200).get().await() }
            }.onSuccess { snap ->
                snap.documents.forEach {
                    items += SearchItem(SearchKind.Lesson, it.id, it.getString("title").orEmpty(), it.getString("passage").orEmpty(), it.getString("body").orEmpty(), it.getTimestamp("createdAt")?.toDate())
                }
            }.onFailure { failures++ }

            if (items.isNotEmpty()) loadedAt = System.currentTimeMillis()
            _state.update { SearchState(loading = false, failed = failures == 4, items = items) }
        }
    }
}

/** The part of [text] around the first place [query] appears (or the start of the text). */
fun snippetOf(text: String, query: String): String {
    val flat = text.replace('\n', ' ')
    val at = flat.indexOf(query, ignoreCase = true)
    if (at < 0) return flat.take(90)
    val start = (at - 30).coerceAtLeast(0)
    val end = (at + query.length + 60).coerceAtMost(flat.length)
    return (if (start > 0) "\u2026" else "") + flat.substring(start, end) + (if (end < flat.length) "\u2026" else "")
}
