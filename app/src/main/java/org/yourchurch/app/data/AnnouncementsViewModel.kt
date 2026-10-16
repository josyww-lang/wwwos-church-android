package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnnouncementsState(
    val today: List<Announcement> = emptyList(),
    val upcoming: List<Announcement> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false
)

class AnnouncementsViewModel : ViewModel() {
    private val repo = AnnouncementRepository()

    private val _state = MutableStateFlow(AnnouncementsState())
    val state: StateFlow<AnnouncementsState> = _state.asStateFlow()

    private var jobs: List<Job> = emptyList()

    /** Admins also see announcements that are waiting for their publishing time. */
    private var showScheduled = false

    fun allowScheduled(value: Boolean) {
        showScheduled = value
    }

    private fun List<Announcement>.visible(): List<Announcement> {
        val now = Date()
        return filter { showScheduled || it.publishAt == null || !it.publishAt.after(now) }
    }
    private var listeningDay: LocalDate? = null

    /** Call whenever the screen is shown. Restarts the listeners if the date has changed. */
    fun start() {
        val today = LocalDate.now()
        if (listeningDay == today && jobs.isNotEmpty()) return
        listeningDay = today
        jobs.forEach { it.cancel() }

        val startOfToday: Date = today.toStartOfDayDate()
        val startOfTomorrow: Date = today.plusDays(1).toStartOfDayDate()

        jobs = listOf(
            viewModelScope.launch {
                repo.observe(startOfToday, startOfTomorrow).collect { result ->
                    result.onSuccess { list ->
                        // Pinned first, then newest first.
                        val sorted = list.visible().sortedWith(
                            compareByDescending<Announcement> { it.pinned }
                                .thenByDescending { it.createdAt }
                        )
                        _state.update { it.copy(today = sorted, loading = false, loadFailed = false) }
                    }.onFailure {
                        _state.update { s ->
                            s.copy(loading = false, loadFailed = true)
                        }
                    }
                }
            },
            viewModelScope.launch {
                repo.observe(startOfTomorrow, null).collect { result ->
                    result.onSuccess { list -> _state.update { it.copy(upcoming = list.visible()) } }
                }
            }
        )
    }

    // Admin actions. The Firestore rules enforce that only admins can do these.
    suspend fun save(a: Announcement): Result<Unit> = runCatching { repo.save(a) }

    fun togglePin(a: Announcement) {
        viewModelScope.launch { runCatching { repo.setPinned(a.id, !a.pinned) } }
    }

    fun delete(a: Announcement) {
        viewModelScope.launch { runCatching { repo.delete(a.id) } }
    }
}
