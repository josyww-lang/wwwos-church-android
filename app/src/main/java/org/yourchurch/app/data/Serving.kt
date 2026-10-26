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

/** People sign up themselves. */
const val SERVE_SIGNUP = "signup"

/** A leader chose the people (a schedule). */
const val SERVE_ASSIGNED = "assigned"

/**
 * One duty on one day, for example "Preaching, Sunday 10:30".
 * - [mode] signup: people sign up themselves (up to [needed]). assigned: the leaders chose the people ([volunteers]); only they are told.
 * - [cantMake]: the assigned people who said they cannot come (the leaders are told, to find someone else).
 * - [durationMin]: how long it lasts (for the end of the calendar entry).
 */
data class ServingSlot(
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val start: Date = Date(),
    val needed: Int = 1,
    val volunteers: List<String> = emptyList(),
    /** uid -> name of the people who signed up, or were assigned. */
    val volunteerNames: Map<String, String> = emptyMap(),
    val createdBy: String = "",
    val mode: String = SERVE_SIGNUP,
    val durationMin: Int = 90,
    val cantMake: List<String> = emptyList(),
    /** uid -> what that person said when they could not come (a comment, a date that suits them). */
    val cantInfo: Map<String, CantInfo> = emptyMap(),
    /** The program of that day was cancelled: the duty stays on the list (greyed) but is not reminded and not added to calendars. */
    val cancelled: Boolean = false,
    /** True for a duty made before "mode" existed (it is a sign-up duty; a leader's phone writes the mode on it). */
    val modeMissing: Boolean = false,
    /** A leader made this duty open for sign-up ("One duty" with "People sign up"). Only such duties are shown to everyone. */
    val open: Boolean = false,
    /** The people on the duty who said "I'll be there". The red number on their phone stays until they say yes or no. */
    val confirmed: List<String> = emptyList()
) {
    val full: Boolean get() = volunteers.size >= needed
    val assigned: Boolean get() = mode == SERVE_ASSIGNED
}

/** What a person told the leaders when they cannot come: a short comment, and maybe a date that suits them better. */
data class CantInfo(val note: String = "", val suggest: Date? = null, val name: String = "")

/** A duty the leaders added besides the four that are built in (Deaconia, Preaching, Program leading, Kids teaching), with its colour (0 to 7). */
data class ServingDuty(val id: String = "", val name: String = "", val color: Int = 4)

/** An approved member who can be chosen for a duty. */
data class ServingMember(val uid: String, val name: String)

class ServingRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val col = db.collection("servingSlots")

    /** Slots from the start of today onward, soonest first (three months of several duties fit in 300). */
    fun observeUpcoming(from: Date): Flow<Result<List<ServingSlot>>> = callbackFlow {
        val registration = col.whereGreaterThanOrEqualTo("start", Timestamp(from)).orderBy("start").limit(300)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(Result.success(snapshot.documents.map { it.toSlot() }))
                }
            }
        awaitClose { registration.remove() }
    }

    /** The duties of one person from the start of today onward: nothing else is loaded, so nothing of other people's duties reaches their phone. */
    fun observeMine(uid: String, from: Date): Flow<Result<List<ServingSlot>>> = callbackFlow {
        val registration = col.whereArrayContains("volunteers", uid).limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(Result.success(snapshot.documents.map { it.toSlot() }.filter { !it.start.before(from) }.sortedBy { it.start }))
                }
            }
        awaitClose { registration.remove() }
    }

    /** The duties people can sign up for (from today onward). Nothing assigned to other people is in it. */
    fun observeOpen(from: Date): Flow<Result<List<ServingSlot>>> = callbackFlow {
        val registration = col.whereEqualTo("open", true).limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(
                        Result.success(snapshot.documents.map { it.toSlot() }.filter { !it.start.before(from) && !it.cancelled }.sortedBy { it.start })
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    private fun ServingSlot.toData(authorUid: String): HashMap<String, Any> = hashMapOf(
        "title" to title,
        "notes" to notes,
        "start" to Timestamp(start),
        "needed" to needed,
        "volunteers" to volunteers,
        "volunteerNames" to volunteerNames,
        "createdBy" to createdBy.ifEmpty { authorUid },
        "createdAt" to Timestamp.now(),
        "mode" to mode,
        "durationMin" to durationMin,
        "cantMake" to cantMake,
        "cantInfo" to cantInfo.mapValues { (_, v) ->
            buildMap<String, Any> {
                put("note", v.note.take(200))
                if (v.name.isNotBlank()) put("name", v.name.take(60))
                v.suggest?.let { put("suggest", Timestamp(it)) }
            }
        },
        "cancelled" to cancelled,
        "open" to open,
        "confirmed" to confirmed
    )

    suspend fun save(slot: ServingSlot, authorUid: String) {
        val data = slot.toData(authorUid)
        withTimeout(15_000) {
            if (slot.id.isEmpty()) col.add(data).await() else col.document(slot.id).set(data).await()
        }
    }

    /** A whole schedule at once (a batch holds up to 500, much more than three months need). */
    suspend fun saveMany(slots: List<ServingSlot>, authorUid: String) {
        withTimeout(30_000) {
            val batch = db.batch()
            slots.forEach { batch.set(col.document(), it.toData(authorUid)) }
            batch.commit().await()
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

    /**
     * An assigned person says they cannot come, with an optional comment and an optional date that suits them (or takes it back).
     * The leaders are told by the notification server.
     */
    suspend fun setCantMake(slot: ServingSlot, uid: String, cant: Boolean, info: CantInfo? = null) {
        val updates: Map<String, Any> = if (cant) {
            mapOf(
                "cantMake" to FieldValue.arrayUnion(uid),
                "cantInfo.$uid" to buildMap<String, Any> {
                    put("note", info?.note.orEmpty().trim().take(200))
                    if (!info?.name.isNullOrBlank()) put("name", info?.name.orEmpty().take(60))
                    info?.suggest?.let { put("suggest", Timestamp(it)) }
                }
            )
        } else {
            mapOf("cantMake" to FieldValue.arrayRemove(uid), "cantInfo.$uid" to FieldValue.delete())
        }
        withTimeout(15_000) { col.document(slot.id).update(updates).await() }
    }

    /** Changes some fields of many duties at once (to cancel a day, or to move days to later dates). */
    suspend fun updateMany(changes: List<Pair<String, Map<String, Any>>>) {
        if (changes.isEmpty()) return
        withTimeout(30_000) {
            val batch = db.batch()
            changes.forEach { (id, fields) -> batch.update(col.document(id), fields) }
            batch.commit().await()
        }
    }

    /** "I'll be there" (or taking it back). */
    suspend fun setConfirmed(slot: ServingSlot, uid: String, on: Boolean) {
        withTimeout(15_000) {
            col.document(slot.id).update("confirmed", if (on) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)).await()
        }
    }

    /** Deletes many duties at once (up to 400 in one go). */
    suspend fun deleteMany(ids: List<String>) {
        ids.chunked(400).forEach { group ->
            withTimeout(30_000) {
                val batch = db.batch()
                group.forEach { batch.delete(col.document(it)) }
                batch.commit().await()
            }
        }
    }

    /** Someone who signed up says they cannot come after all: they leave the duty, and the leaders get their comment. */
    suspend fun withdrawWithInfo(slot: ServingSlot, uid: String, info: CantInfo) {
        val updates: Map<String, Any> = mapOf(
            "volunteers" to FieldValue.arrayRemove(uid),
            "volunteerNames.$uid" to FieldValue.delete(),
            "cantInfo.$uid" to buildMap<String, Any> {
                put("note", info.note.trim().take(200))
                put("name", info.name.take(60))
                info.suggest?.let { put("suggest", Timestamp(it)) }
            }
        )
        withTimeout(15_000) { col.document(slot.id).update(updates).await() }
    }

    /**
     * Asks the notification server to tell these people (and only them): kind is "assigned", "changed" or "removed".
     * [count] is how many dates it is about, [start] the first one.
     */
    suspend fun notify(kind: String, uids: List<String>, title: String, start: Date, count: Int, authorUid: String) {
        if (uids.isEmpty()) return
        uids.chunked(50).forEach { group ->
            withTimeout(15_000) {
                db.collection("servingNotices").add(
                    hashMapOf(
                        "uids" to group,
                        "kind" to kind,
                        "title" to title.take(80),
                        "start" to Timestamp(start),
                        "count" to count.coerceIn(1, 60),
                        "createdBy" to authorUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    /** The duties the leaders added. */
    fun observeDuties(): Flow<Result<List<ServingDuty>>> = callbackFlow {
        val registration = db.collection("servingDuties").addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
            } else if (snapshot != null) {
                trySend(
                    Result.success(
                        snapshot.documents.map { d ->
                            ServingDuty(d.id, d.getString("name") ?: "", (d.getLong("color") ?: 4L).toInt())
                        }.filter { it.name.isNotBlank() }.sortedBy { it.name.lowercase() }
                    )
                )
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun addDuty(name: String, color: Int, authorUid: String) {
        withTimeout(15_000) {
            db.collection("servingDuties").add(
                hashMapOf("name" to name.trim().take(40), "color" to color, "createdBy" to authorUid, "createdAt" to Timestamp.now())
            ).await()
        }
    }

    /** The approved members, by name (the leaders choose among them). */
    suspend fun members(): List<ServingMember> = withTimeout(15_000) {
        db.collection("approvedMembers").get().await().documents
            .map { ServingMember(it.id, (it.getString("name") ?: "").trim()) }
            .filter { it.name.isNotEmpty() }
            .sortedBy { it.name.lowercase() }
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
        createdBy = getString("createdBy") ?: "",
        mode = getString("mode") ?: SERVE_SIGNUP,
        durationMin = (getLong("durationMin") ?: 90L).toInt(),
        cantMake = (get("cantMake") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        cantInfo = (get("cantInfo") as? Map<*, *>)?.entries?.associate { (key, value) ->
            val m = value as? Map<*, *>
            key.toString() to CantInfo(
                note = (m?.get("note") as? String).orEmpty(),
                suggest = (m?.get("suggest") as? Timestamp)?.toDate(),
                name = (m?.get("name") as? String).orEmpty()
            )
        } ?: emptyMap(),
        cancelled = getBoolean("cancelled") == true,
        modeMissing = !contains("mode"),
        open = getBoolean("open") == true,
        confirmed = (get("confirmed") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    )
}

data class ServingState(
    val slots: List<ServingSlot> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val actionFailed: Boolean = false,
    val members: List<ServingMember> = emptyList(),
    val membersLoading: Boolean = false,
    /** The duties the leaders added (the four built-in ones are not in this list). */
    val duties: List<ServingDuty> = emptyList()
)

class ServingViewModel : ViewModel() {
    private val repo = ServingRepository()

    private val _state = MutableStateFlow(ServingState())
    val state: StateFlow<ServingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeDuties().collect { result -> result.onSuccess { list -> _state.update { it.copy(duties = list) } } }
        }
    }

    private var loadJob: kotlinx.coroutines.Job? = null
    private var loadedAsLeader: Boolean? = null

    /** Starts loading the duties: a leader loads all of them, everyone else only the duties they are assigned to. */
    fun start(uid: String, isAdmin: Boolean) {
        if (loadedAsLeader == isAdmin) return
        loadedAsLeader = isAdmin
        loadJob?.cancel()
        val startOfToday = java.time.LocalDate.now().toStartOfDayDate()
        loadJob = viewModelScope.launch {
            if (isAdmin) {
                var migrated = false
                repo.observeUpcoming(startOfToday).collect { result ->
                    result.onSuccess { list ->
                        _state.update { it.copy(slots = list, loading = false, loadFailed = false) }
                        // The first duties (made before "mode" existed) were all sign-up duties: mark them once, so people still find them.
                        val old = list.filter { it.modeMissing }
                        if (old.isNotEmpty() && !migrated) {
                            migrated = true
                            runCatching { repo.updateMany(old.map { it.id to mapOf<String, Any>("mode" to SERVE_SIGNUP, "open" to true) }) }
                        }
                    }.onFailure {
                        _state.update { it.copy(loading = false, loadFailed = true) }
                    }
                }
            } else {
                // A member sees their own duties and the duties open for sign-up: nothing else.
                kotlinx.coroutines.flow.combine(repo.observeMine(uid, startOfToday), repo.observeOpen(startOfToday)) { mine, open ->
                    val list = (mine.getOrDefault(emptyList()) + open.getOrDefault(emptyList())).distinctBy { it.id }.sortedBy { it.start }
                    list to (mine.isFailure && open.isFailure)
                }.collect { (list, failed) ->
                    _state.update { it.copy(slots = list, loading = false, loadFailed = failed) }
                }
            }
        }
    }

    /** A new duty (for example "Sound" or "Cleaning"): it appears with the others from then on. It gets the next colour. */
    suspend fun addDuty(name: String, authorUid: String): Result<Unit> = runCatching {
        val clean = name.trim()
        if (clean.isNotEmpty() && _state.value.duties.none { it.name.equals(clean, ignoreCase = true) }) {
            repo.addDuty(clean, 4 + (_state.value.duties.size % 4), authorUid)
        }
    }

    /** Loads the members the leaders choose among (once). */
    fun loadMembers() {
        val s = _state.value
        if (s.members.isNotEmpty() || s.membersLoading) return
        _state.update { it.copy(membersLoading = true) }
        viewModelScope.launch {
            val list = runCatching { repo.members() }.getOrDefault(emptyList())
            _state.update { it.copy(members = list, membersLoading = false) }
        }
    }

    /** Saves one slot. If the people were chosen by a leader, the people added, removed or affected by a change are told (only them). */
    suspend fun save(slotAsEdited: ServingSlot, authorUid: String): Result<Unit> = runCatching {
        val before = _state.value.slots.firstOrNull { it.id == slotAsEdited.id }
        // A change of day means the people must say yes again; otherwise their "I'll be there" stays.
        val slot = slotAsEdited.copy(
            confirmed = if (before != null && before.start == slotAsEdited.start) before.confirmed.filter { it in slotAsEdited.volunteers } else emptyList()
        )
        repo.save(slot, authorUid)
        if (slot.assigned || before?.assigned == true) {
            val old = if (before?.assigned == true) before.volunteers.toSet() else emptySet()
            val now = if (slot.assigned) slot.volunteers.toSet() else emptySet()
            val changed = before != null && (before.start != slot.start || before.title != slot.title)
            runCatching {
                repo.notify("assigned", (now - old).toList(), slot.title, slot.start, 1, authorUid)
                repo.notify("removed", (old - now).toList(), (before ?: slot).title, (before ?: slot).start, 1, authorUid)
                if (changed) repo.notify("changed", (now intersect old).toList(), slot.title, slot.start, 1, authorUid)
            }
        }
    }

    /** Publishes a whole schedule, then tells each person once (not once for every date), with all their duties. */
    suspend fun publishSchedule(slots: List<ServingSlot>, authorUid: String): Result<Unit> = runCatching {
        repo.saveMany(slots, authorUid)
        tell("assigned", slots.filter { it.assigned }.map { it to it.start }, authorUid)
    }

    /** Deletes the duties in the list (for example every "Deaconia" to plan it again). The people who were on them are told once. */
    suspend fun deleteMany(slots: List<ServingSlot>, authorUid: String): Result<Unit> = runCatching {
        if (slots.isNotEmpty()) {
            repo.deleteMany(slots.map { it.id })
            tell("removed", slots.filter { !it.cancelled }.map { it to it.start }, authorUid)
        }
    }

    /** Deletes one duty. The result says whether it worked (and why not), so a failure is never silent. */
    suspend fun deleteOne(slot: ServingSlot, authorUid: String): Result<Unit> = runCatching {
        repo.delete(slot)
        // The people were told after the delete worked; a failure to tell them does not undo it.
        if (slot.assigned) runCatching { repo.notify("removed", slot.volunteers, slot.title, slot.start, 1, authorUid) }
        Unit
    }

    fun toggleSignUp(slot: ServingSlot, uid: String, name: String) {
        viewModelScope.launch {
            val result = runCatching {
                if (uid in slot.volunteers) repo.withdraw(slot, uid) else repo.signUp(slot, uid, name)
            }
            _state.update { it.copy(actionFailed = result.isFailure) }
        }
    }

    /** "I can't make it" for an assigned duty, with an optional comment and an optional date that suits the person better. */
    fun sayCantMake(slot: ServingSlot, uid: String, name: String, note: String, suggest: Date?) {
        viewModelScope.launch {
            val result = runCatching { repo.setCantMake(slot, uid, true, CantInfo(note, suggest, name)) }
            _state.update { it.copy(actionFailed = result.isFailure) }
        }
    }

    /** Someone who signed up leaves the duty and tells the leaders why (and maybe a date that suits them better). */
    fun withdrawWithReason(slot: ServingSlot, uid: String, name: String, note: String, suggest: Date?) {
        viewModelScope.launch {
            val result = runCatching { repo.withdrawWithInfo(slot, uid, CantInfo(note, suggest, name)) }
            _state.update { it.copy(actionFailed = result.isFailure) }
        }
    }

    /** Tells each person once about all their duties in [items] (a duty with the date to mention: the new date, for a move). */
    private suspend fun tell(kind: String, items: List<Pair<ServingSlot, Date>>, authorUid: String) {
        val perPerson = HashMap<String, MutableList<Pair<ServingSlot, Date>>>()
        items.forEach { (slot, date) -> slot.volunteers.forEach { perPerson.getOrPut(it) { mutableListOf() }.add(slot to date) } }
        runCatching {
            perPerson.forEach { (person, list) ->
                val first = list.minByOrNull { it.second } ?: return@forEach
                val title = list.map { it.first.title.trim() }.distinct().joinToString(", ").take(80)
                repo.notify(kind, listOf(person), title, first.second, list.size, authorUid)
            }
        }
    }

    /** Whether a duty is one of the chosen duties (no choice means all of them). */
    private fun inDuties(slot: ServingSlot, duties: Set<String>) =
        duties.isEmpty() || duties.any { it.trim().equals(slot.title.trim(), ignoreCase = true) }

    /** The program of a day is cancelled: its duties stay on the list, marked as cancelled, and the people on them are told. Nothing else moves. */
    suspend fun cancelDay(day: java.time.LocalDate, authorUid: String, duties: Set<String> = emptySet()): Result<Unit> = runCatching {
        val hit = _state.value.slots.filter { it.start.toLocalDate() == day && !it.cancelled && inDuties(it, duties) }
        if (hit.isNotEmpty()) {
            repo.updateMany(hit.map { it.id to mapOf<String, Any>("cancelled" to true) })
            tell("cancelled", hit.map { it to it.start }, authorUid)
        }
    }

    /**
     * Pushes the duties of a day [weeks] weeks ahead: only that day, or that day and every later day (so the order of the schedule is kept).
     * The time of day stays the same. The people on the moved duties are told once.
     */
    suspend fun pushDays(day: java.time.LocalDate, weeks: Int, andAfter: Boolean, authorUid: String, duties: Set<String> = emptySet()): Result<Unit> = runCatching {
        val zone = java.time.ZoneId.systemDefault()
        val hit = _state.value.slots.filter { s ->
            val d = s.start.toLocalDate()
            (if (andAfter) !d.isBefore(day) else d == day) && inDuties(s, duties)
        }
        if (hit.isNotEmpty()) {
            val moved = hit.map { it to Date.from(it.start.toInstant().atZone(zone).plusWeeks(weeks.toLong()).toInstant()) }
            // A moved duty: the people say yes again.
            repo.updateMany(moved.map { (slot, date) -> slot.id to mapOf<String, Any>("start" to Timestamp(date), "confirmed" to emptyList<String>()) })
            tell("changed", moved, authorUid)
        }
    }

    /** A cancelled duty is back on. */
    suspend fun restore(slot: ServingSlot, authorUid: String): Result<Unit> = runCatching {
        repo.updateMany(listOf(slot.id to mapOf<String, Any>("cancelled" to false)))
        tell("assigned", listOf(slot to slot.start), authorUid)
    }

    /** "I'll be there": the person accepts the duty (and the red number on their phone goes away). */
    fun confirm(slot: ServingSlot, uid: String) {
        viewModelScope.launch {
            val result = runCatching { repo.setConfirmed(slot, uid, uid !in slot.confirmed) }
            _state.update { it.copy(actionFailed = result.isFailure) }
        }
    }

    /** Takes "I can't make it" back. */
    fun undoCantMake(slot: ServingSlot, uid: String) {
        viewModelScope.launch {
            val result = runCatching { repo.setCantMake(slot, uid, false) }
            _state.update { it.copy(actionFailed = result.isFailure) }
        }
    }

    fun clearActionFailed() = _state.update { it.copy(actionFailed = false) }
}

/** A person who has more than one duty on the same day (allowed: it is only shown to the leaders as a note). */
data class DoubleDuty(val date: java.time.LocalDate, val uid: String, val name: String, val duties: List<String>)

/** Finds the people with two or more different duties on the same day (cancelled duties and sign-up duties are not counted). */
fun doubleDuties(slots: List<ServingSlot>): List<DoubleDuty> =
    slots.filter { it.assigned && !it.cancelled }
        .groupBy { it.start.toLocalDate() }
        .flatMap { (day, list) ->
            val duties = HashMap<String, MutableList<String>>()
            val names = HashMap<String, String>()
            list.forEach { s ->
                s.volunteers.forEach { u ->
                    val own = duties.getOrPut(u) { mutableListOf() }
                    if (own.none { it.equals(s.title.trim(), ignoreCase = true) }) own.add(s.title.trim())
                    names.putIfAbsent(u, s.volunteerNames[u].orEmpty())
                }
            }
            duties.filter { it.value.size >= 2 }.map { (u, own) -> DoubleDuty(day, u, NameDirectory.nameOf(u, names[u].orEmpty()), own) }
        }
        .sortedWith(compareBy({ it.date }, { it.name }))
