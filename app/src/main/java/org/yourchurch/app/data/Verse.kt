package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The English verse of the day that the notification server writes each morning (used when the church's list has none). */
data class DailyVerse(val reference: String = "", val text: String = "")

class VerseViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _verse = MutableStateFlow<DailyVerse?>(null)
    val verse: StateFlow<DailyVerse?> = _verse.asStateFlow()
    private val dailyRegistration: ListenerRegistration =
        db.collection("settings").document("verseOfDay").addSnapshotListener { snapshot, _ ->
            val text = snapshot?.getString("text").orEmpty()
            _verse.value = if (text.isBlank()) null else DailyVerse(snapshot?.getString("reference").orEmpty(), text)
        }

    /** Today's verse from the church's own list (a different one each day; it starts again after the last day). */
    private val _bank = MutableStateFlow<BankVerse?>(null)
    val bank: StateFlow<BankVerse?> = _bank.asStateFlow()
    private var bankRegistration: ListenerRegistration? = null
    private val metaRegistration: ListenerRegistration =
        db.collection("settings").document("verseBank").addSnapshotListener { snapshot, _ ->
            val count = (snapshot?.getLong("count") ?: 0L).toInt()
            bankRegistration?.remove()
            bankRegistration = null
            if (count <= 0) {
                _bank.value = null
                return@addSnapshotListener
            }
            val day = (LocalDate.now().toEpochDay() % count).toInt() + 1
            bankRegistration = db.collection("verseBank").document(day.toString()).addSnapshotListener { doc, _ ->
                _bank.value = if (doc != null && doc.exists()) doc.toBankVerse() else null
            }
        }

    override fun onCleared() {
        dailyRegistration.remove()
        metaRegistration.remove()
        bankRegistration?.remove()
    }
}
