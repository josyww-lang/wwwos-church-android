package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether this person may add Study content (lessons, files, audio, voice notes) and reading plans. */
class ContributorViewModel : ViewModel() {
    private val _can = MutableStateFlow(false)
    val can: StateFlow<Boolean> = _can.asStateFlow()
    private var registration: ListenerRegistration? = null
    private var uid = ""

    fun start(uid: String) {
        if (this.uid == uid) return
        this.uid = uid
        registration?.remove()
        registration = FirebaseFirestore.getInstance().collection("contributors").document(uid)
            .addSnapshotListener { snapshot, _ -> _can.value = snapshot?.exists() == true }
    }

    override fun onCleared() {
        registration?.remove()
    }
}
