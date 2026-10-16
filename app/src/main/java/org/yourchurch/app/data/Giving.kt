package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
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

/** How people can give. Stored in Firestore at settings/giving so admins can change it in the app. */
data class GivingInfo(
    val vippsNumber: String = "",
    val accountName: String = "",
    val accountNumber: String = "",
    val iban: String = "",
    val note: String = ""
) {
    val hasVipps get() = vippsNumber.isNotBlank()
    val hasBank get() = accountNumber.isNotBlank() || iban.isNotBlank()
}

class GivingRepository(db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val ref = db.collection("settings").document("giving")

    fun observe(): Flow<Result<GivingInfo>> = callbackFlow {
        val registration = ref.addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(Result.failure(error))
            } else if (snap != null) {
                trySend(
                    Result.success(
                        GivingInfo(
                            vippsNumber = snap.getString("vippsNumber") ?: "",
                            accountName = snap.getString("accountName") ?: "",
                            accountNumber = snap.getString("accountNumber") ?: "",
                            iban = snap.getString("iban") ?: "",
                            note = snap.getString("note") ?: ""
                        )
                    )
                )
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun save(info: GivingInfo) {
        val data = hashMapOf(
            "vippsNumber" to info.vippsNumber,
            "accountName" to info.accountName,
            "accountNumber" to info.accountNumber,
            "iban" to info.iban,
            "note" to info.note
        )
        withTimeout(15_000) { ref.set(data).await() }
    }
}

data class GivingState(
    val info: GivingInfo = GivingInfo(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false
)

class GivingViewModel : ViewModel() {
    private val repo = GivingRepository()

    private val _state = MutableStateFlow(GivingState())
    val state: StateFlow<GivingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observe().collect { result ->
                result.onSuccess { info ->
                    _state.update { s -> s.copy(info = info, loading = false, loadFailed = false) }
                }.onFailure {
                    _state.update { s -> s.copy(loading = false, loadFailed = true) }
                }
            }
        }
    }

    suspend fun save(info: GivingInfo): Result<Unit> = runCatching { repo.save(info) }
}
