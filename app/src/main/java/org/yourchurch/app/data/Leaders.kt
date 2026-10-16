package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** A member, and whether an owner has given them leader access (and until when). */
data class LeaderRow(val uid: String, val name: String, val isLeader: Boolean, val until: Date?, val isContributor: Boolean = false) {
    /** True when the leader access has run out. */
    val expired: Boolean get() = isLeader && until != null && until.before(Date())
}

/** Roles: a member can be a study contributor, or (given by an owner) a leader, for a term or with no end date. */
class LeadersAdminViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val _rows = MutableStateFlow<List<LeaderRow>>(emptyList())
    val rows: StateFlow<List<LeaderRow>> = _rows.asStateFlow()
    private var members: Map<String, String> = emptyMap()
    private var leaders: Map<String, Pair<String, Date?>> = emptyMap()
    private var contributors: Set<String> = emptySet()
    private val registrations = mutableListOf<ListenerRegistration>()

    init {
        registrations += db.collection("approvedMembers").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            members = snapshot.documents.associate { it.id to (it.getString("name") ?: "") }
            publish()
        }
        registrations += db.collection("leaders").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            leaders = snapshot.documents.associate { it.id to ((it.getString("name") ?: "") to it.getTimestamp("until")?.toDate()) }
            publish()
        }
        registrations += db.collection("contributors").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            contributors = snapshot.documents.map { it.id }.toSet()
            publish()
        }
    }

    private fun publish() {
        val ids = members.keys + leaders.keys
        _rows.value = ids.map { id ->
            val leader = leaders[id]
            LeaderRow(id, members[id] ?: leader?.first.orEmpty(), leader != null, leader?.second, id in contributors)
        }.sortedWith(compareByDescending<LeaderRow> { it.isLeader }.thenBy { it.name.lowercase() })
    }

    /** Sets what this person can do: 0 member, 1 study contributor, 2 leader ([until] is the end of the term, or null). */
    fun setRole(row: LeaderRow, role: Int, until: Date?) {
        viewModelScope.launch {
            val leaderDoc = db.collection("leaders").document(row.uid)
            val contributorDoc = db.collection("contributors").document(row.uid)
            runCatching {
                when (role) {
                    2 -> {
                        val data = hashMapOf<String, Any>(
                            "name" to row.name.take(60),
                            "grantedBy" to FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                            "grantedAt" to Timestamp.now()
                        )
                        if (until != null) data["until"] = Timestamp(until)
                        leaderDoc.set(data).await()
                        runCatching { contributorDoc.delete().await() }   // a leader can do all of it already
                    }
                    1 -> {
                        contributorDoc.set(mapOf("name" to row.name.take(60), "grantedAt" to Timestamp.now())).await()
                        if (row.isLeader) runCatching { leaderDoc.delete().await() }
                    }
                    else -> {
                        runCatching { contributorDoc.delete().await() }
                        if (row.isLeader) runCatching { leaderDoc.delete().await() }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        registrations.forEach { it.remove() }
    }
}
