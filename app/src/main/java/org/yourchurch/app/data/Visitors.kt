package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Someone who visited the church and said hello in the app (they do not need an account to do this). */
data class Visitor(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val message: String = "",
    val createdAt: Date? = null,
    val contacted: Boolean = false
)

class VisitorsViewModel : ViewModel() {
    private val col = FirebaseFirestore.getInstance().collection("visitors")
    private val _visitors = MutableStateFlow<List<Visitor>>(emptyList())
    val visitors: StateFlow<List<Visitor>> = _visitors.asStateFlow()
    private var registration: ListenerRegistration? = null

    /** Leaders only: start listening to the list of visitors. */
    fun watch() {
        if (registration != null) return
        registration = col.orderBy("createdAt", Query.Direction.DESCENDING).limit(100).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            _visitors.value = snapshot.documents.map {
                Visitor(
                    id = it.id,
                    name = it.getString("name") ?: "",
                    phone = it.getString("phone") ?: "",
                    email = it.getString("email") ?: "",
                    message = it.getString("message") ?: "",
                    createdAt = it.getTimestamp("createdAt")?.toDate(),
                    contacted = it.getBoolean("contacted") == true
                )
            }
        }
    }

    /** Anyone: send a hello to the church leaders. */
    suspend fun send(name: String, phone: String, email: String, message: String): Result<Unit> = runCatching {
        withTimeout(15_000) {
            col.add(
                mapOf(
                    "name" to name, "phone" to phone, "email" to email, "message" to message,
                    "createdAt" to FieldValue.serverTimestamp(), "contacted" to false
                )
            ).await()
        }
    }

    fun setContacted(visitor: Visitor, value: Boolean) {
        viewModelScope.launch { runCatching { col.document(visitor.id).update("contacted", value).await() } }
    }

    fun delete(visitor: Visitor) {
        viewModelScope.launch { runCatching { col.document(visitor.id).delete().await() } }
    }

    override fun onCleared() {
        registration?.remove()
    }
}
