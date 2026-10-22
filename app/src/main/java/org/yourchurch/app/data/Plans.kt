package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** A Bible reading plan: one reading for each day, starting on [startDate]. */
data class ReadingPlan(
    val id: String = "",
    val title: String = "",
    val startDate: Date = Date(),
    val days: List<String> = emptyList(),
    val authorId: String = ""
)

/** How far one person has got (shown to leaders). */
data class PlanPerson(val uid: String, val name: String, val done: Int, val lastActive: Date? = null, val reflected: Int = 0)

data class PlansState(
    val plan: ReadingPlan? = null,
    /** The days (0 = the first) this person has marked as read. */
    val done: Set<Int> = emptySet(),
    /** The dates (yyyy-MM-dd) on which this person read something: for the streak. */
    val activeDays: List<String> = emptyList(),
    /** Where this person chose to continue from (0 = from the start). */
    val from: Int = 0,
    /** When this person last marked a day. */
    val lastReadAt: Date? = null,
    /** The days this person wrote a reflection on (only the days, never what they wrote). */
    val reflected: Set<Int> = emptySet(),
    /** Leaders only: everyone's progress. */
    val everyone: List<PlanPerson> = emptyList(),
    val loading: Boolean = true
)

/** Which day of the plan today is: 0 for the first day, negative before it starts, days.size or more after it ends. */
fun ReadingPlan.todayIndex(): Int = ChronoUnit.DAYS.between(startDate.toLocalDate(), LocalDate.now()).toInt()

/**
 * The person's next reading: the first day they have not read, at or after the day they chose to continue from. If they
 * have read everything from there on, the first unread day before it. [size] when every day is read.
 */
fun nextDayOf(done: Set<Int>, from: Int, size: Int): Int {
    var day = from.coerceIn(0, size)
    while (day < size && day in done) day++
    if (day < size) return day
    day = 0
    while (day < size && day in done) day++
    return day
}

/** How many days in a row, up to today (or yesterday, if today has no reading yet), the person read something. */
fun readingStreak(activeDays: List<String>, today: LocalDate = LocalDate.now()): Int {
    val days = activeDays.toSet()
    var day = if (today.toString() in days) today else today.minusDays(1)
    var count = 0
    while (day.toString() in days) {
        count++
        day = day.minusDays(1)
    }
    return count
}

/** How many days ago this was. */
fun daysSince(date: Date): Int = ChronoUnit.DAYS.between(date.toLocalDate(), LocalDate.now()).toInt()

/** The newest reading plan is the one everyone follows. */
class PlansViewModel : ViewModel() {
    private val plans = FirebaseFirestore.getInstance().collection("readingPlans")
    private val _state = MutableStateFlow(PlansState())
    val state: StateFlow<PlansState> = _state.asStateFlow()

    private var uid = ""
    private var name = ""
    private var isAdmin = false
    private var started = false
    private var planRegistration: ListenerRegistration? = null
    private var mineRegistration: ListenerRegistration? = null
    private var allRegistration: ListenerRegistration? = null

    fun start(uid: String, name: String, isAdmin: Boolean) {
        this.name = name
        if (started && this.isAdmin == isAdmin && this.uid == uid) return
        stopListening()
        started = true
        this.uid = uid
        this.isAdmin = isAdmin
        planRegistration = plans.orderBy("createdAt", Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    _state.update { it.copy(loading = false) }
                    return@addSnapshotListener
                }
                val doc = snapshot.documents.firstOrNull()
                val plan = doc?.let {
                    ReadingPlan(
                        id = it.id,
                        title = it.getString("title") ?: "",
                        startDate = it.getTimestamp("startDate")?.toDate() ?: Date(),
                        days = (it.get("days") as? List<*>)?.map { d -> d?.toString().orEmpty() } ?: emptyList(),
                        authorId = it.getString("authorId") ?: ""
                    )
                }
                _state.update { it.copy(plan = plan, loading = false) }
                watchProgress(plan)
            }
    }

    private fun watchProgress(plan: ReadingPlan?) {
        mineRegistration?.remove()
        allRegistration?.remove()
        if (plan == null) {
            _state.update { it.copy(done = emptySet(), everyone = emptyList()) }
            return
        }
        val progress = plans.document(plan.id).collection("progress")
        mineRegistration = progress.document(uid).addSnapshotListener { snapshot, _ ->
            val days = (snapshot?.get("done") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
            val active = (snapshot?.get("activeDays") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            _state.update {
                it.copy(
                    done = days.toSet(),
                    activeDays = active,
                    from = (snapshot?.getLong("from") ?: 0L).toInt(),
                    lastReadAt = snapshot?.getTimestamp("updatedAt")?.toDate(),
                    reflected = (snapshot?.get("reflected") as? List<*>)?.mapNotNull { n -> (n as? Number)?.toInt() }?.toSet() ?: emptySet()
                )
            }
        }
        if (isAdmin) {
            allRegistration = progress.addSnapshotListener { snapshot, _ ->
                val people = snapshot?.documents?.map {
                    PlanPerson(it.id, it.getString("name") ?: "", (it.get("done") as? List<*>)?.size ?: 0, it.getTimestamp("updatedAt")?.toDate(), (it.get("reflected") as? List<*>)?.size ?: 0)
                }?.sortedByDescending { it.done } ?: emptyList()
                _state.update { it.copy(everyone = people) }
            }
        }
    }

    /** Marks a day as read, or takes the mark away. Any day can be marked: to catch up, or to skip ahead. */
    fun toggleDay(day: Int) {
        val current = _state.value
        if (current.plan == null) return
        val adding = day !in current.done
        val updated = if (adding) current.done + day else current.done - day
        val today = LocalDate.now().toString()
        val active = if (adding && today !in current.activeDays) (current.activeDays + today).takeLast(400) else current.activeDays
        _state.update { it.copy(done = updated, activeDays = active, lastReadAt = Date()) }
        saveProgress(updated, active, current.from)
    }

    /** Marks a day as read (from the end of a chapter in the Bible reader). If it is already marked, nothing changes. */
    fun markRead(day: Int) {
        if (_state.value.plan == null || day in _state.value.done) return
        toggleDay(day)
    }

    /** "Continue from here": the next reading is the first unread one from this day on. */
    fun continueFrom(day: Int) {
        val current = _state.value
        _state.update { it.copy(from = day) }
        saveProgress(current.done, current.activeDays, day)
    }

    /** Starts the plan again from day 1 (only this person's marks are cleared). */
    fun restart() {
        _state.update { it.copy(done = emptySet(), activeDays = emptyList(), from = 0, lastReadAt = null, reflected = emptySet()) }
        saveProgress(emptySet(), emptyList(), 0, emptySet())
    }

    /** The person wrote a reflection on this day (optional). Leaders only see how many days, not what was written. */
    fun markReflected(day: Int) {
        val current = _state.value
        if (current.plan == null || day in current.reflected) return
        val updated = current.reflected + day
        _state.update { it.copy(reflected = updated) }
        saveProgress(current.done, current.activeDays, current.from, updated)
    }

    private fun saveProgress(done: Set<Int>, active: List<String>, from: Int, reflected: Set<Int> = _state.value.reflected) {
        val plan = _state.value.plan ?: return
        viewModelScope.launch {
            runCatching {
                withTimeout(15_000) {
                    plans.document(plan.id).collection("progress").document(uid)
                        .set(
                            mapOf(
                                "name" to name.take(60),
                                "done" to done.sorted(),
                                "activeDays" to active,
                                "from" to from,
                                "reflected" to reflected.sorted(),
                                "updatedAt" to Timestamp.now()
                            )
                        ).await()
                }
            }
        }
    }

    suspend fun createPlan(title: String, start: Date, days: List<String>, authorUid: String): Result<Unit> = runCatching {
        withTimeout(15_000) {
            plans.add(
                mapOf("title" to title, "startDate" to Timestamp(start), "days" to days, "createdAt" to Timestamp.now(), "authorId" to authorUid)
            ).await()
        }
    }

    fun deletePlan(plan: ReadingPlan) {
        viewModelScope.launch { runCatching { plans.document(plan.id).delete().await() } }
    }

    private fun stopListening() {
        planRegistration?.remove()
        mineRegistration?.remove()
        allRegistration?.remove()
    }

    override fun onCleared() {
        stopListening()
    }
}
