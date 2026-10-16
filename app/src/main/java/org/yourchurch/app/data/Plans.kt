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
data class PlanPerson(val uid: String, val name: String, val done: Int)

data class PlansState(
    val plan: ReadingPlan? = null,
    /** The days (0 = the first) this person has marked as read. */
    val done: Set<Int> = emptySet(),
    /** Leaders only: everyone's progress. */
    val everyone: List<PlanPerson> = emptyList(),
    val loading: Boolean = true
)

/** Which day of the plan today is: 0 for the first day, negative before it starts, days.size or more after it ends. */
fun ReadingPlan.todayIndex(): Int = ChronoUnit.DAYS.between(startDate.toLocalDate(), LocalDate.now()).toInt()

/** How many days in a row, up to today (or yesterday, if today is not read yet), have been read. */
fun streakOf(done: Set<Int>, today: Int): Int {
    var day = if (today in done) today else today - 1
    var count = 0
    while (day >= 0 && day in done) {
        count++
        day--
    }
    return count
}

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
            _state.update { it.copy(done = days.toSet()) }
        }
        if (isAdmin) {
            allRegistration = progress.addSnapshotListener { snapshot, _ ->
                val people = snapshot?.documents?.map {
                    PlanPerson(it.id, it.getString("name") ?: "", (it.get("done") as? List<*>)?.size ?: 0)
                }?.sortedByDescending { it.done } ?: emptyList()
                _state.update { it.copy(everyone = people) }
            }
        }
    }

    /** Marks a day as read, or takes the mark away. */
    fun toggleDay(day: Int) {
        val plan = _state.value.plan ?: return
        val current = _state.value.done
        val updated = if (day in current) current - day else current + day
        _state.update { it.copy(done = updated) }
        viewModelScope.launch {
            runCatching {
                withTimeout(15_000) {
                    plans.document(plan.id).collection("progress").document(uid)
                        .set(mapOf("name" to name.take(60), "done" to updated.sorted(), "updatedAt" to Timestamp.now())).await()
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
