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

/** The church's contact details and welcome texts. Stored in Firestore at settings/church; admins edit them in the app. */
data class ChurchInfo(
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val facebook: String = "",
    val youtube: String = "",
    val serviceTimes: String = "",
    val welcomeEn: String = "",
    val welcomeNb: String = "",
    val welcomeOm: String = "",
    val newHereEn: String = "",
    val newHereNb: String = "",
    val newHereOm: String = "",
    /** The link to download the app (Google Play, or a Drive or Firebase App Distribution link until then); used by "Share the app". */
    val appLink: String = ""
) {

    val hasAnyContact: Boolean
        get() = listOf(address, phone, email, website, facebook, youtube, serviceTimes).any { it.isNotBlank() }

    /** The "I'm new here" page text in the person's language, or another one if that one is empty. */
    fun newHereFor(languageCode: String): String {
        val ordered = when (languageCode) {
            "om" -> listOf(newHereOm, newHereNb, newHereEn)
            "nb", "nn", "no" -> listOf(newHereNb, newHereEn, newHereOm)
            else -> listOf(newHereEn, newHereNb, newHereOm)
        }
        return ordered.firstOrNull { it.isNotBlank() } ?: ""
    }

    /** The welcome text in the person's language, or another one if that one is empty. */
    fun welcomeFor(languageCode: String): String {
        val ordered = when (languageCode) {
            "om" -> listOf(welcomeOm, welcomeNb, welcomeEn)
            "nb", "nn", "no" -> listOf(welcomeNb, welcomeEn, welcomeOm)
            else -> listOf(welcomeEn, welcomeNb, welcomeOm)
        }
        return ordered.firstOrNull { it.isNotBlank() } ?: ""
    }
}

class ChurchInfoRepository(db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val ref = db.collection("settings").document("church")

    fun observe(): Flow<Result<ChurchInfo>> = callbackFlow {
        val registration = ref.addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(Result.failure(error))
            } else if (snap != null) {
                fun s(key: String) = snap.getString(key) ?: ""
                trySend(
                    Result.success(
                        ChurchInfo(
                            address = s("address"), phone = s("phone"), email = s("email"),
                            website = s("website"), facebook = s("facebook"), youtube = s("youtube"),
                            serviceTimes = s("serviceTimes"),
                            welcomeEn = s("welcomeEn"), welcomeNb = s("welcomeNb"), welcomeOm = s("welcomeOm"),
                            newHereEn = s("newHereEn"), newHereNb = s("newHereNb"), newHereOm = s("newHereOm"),
                            appLink = s("appLink")
                        )
                    )
                )
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun save(info: ChurchInfo) {
        val data = hashMapOf(
            "address" to info.address, "phone" to info.phone, "email" to info.email,
            "website" to info.website, "facebook" to info.facebook, "youtube" to info.youtube,
            "serviceTimes" to info.serviceTimes,
            "welcomeEn" to info.welcomeEn, "welcomeNb" to info.welcomeNb, "welcomeOm" to info.welcomeOm,
            "newHereEn" to info.newHereEn, "newHereNb" to info.newHereNb, "newHereOm" to info.newHereOm,
            "appLink" to info.appLink
        )
        withTimeout(15_000) { ref.set(data).await() }
    }
}

data class ChurchInfoState(
    val info: ChurchInfo = ChurchInfo(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false
)

class ChurchInfoViewModel : ViewModel() {
    private val repo = ChurchInfoRepository()

    private val _state = MutableStateFlow(ChurchInfoState())
    val state: StateFlow<ChurchInfoState> = _state.asStateFlow()

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

    suspend fun save(info: ChurchInfo): Result<Unit> = runCatching { repo.save(info) }
}
