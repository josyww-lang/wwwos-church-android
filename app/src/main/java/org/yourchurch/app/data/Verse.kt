package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One verse in one language: the words, the reference as that Bible writes it, and the credit (version and copyright) to show under it. */
data class VerseLine(val text: String, val reference: String, val credit: String = "")

/**
 * The verse of the day that the notification server writes each night: the same verse in English, Norsk and Afaan
 * Oromoo, from the online Bible (API.Bible). Any of them can be empty (for example if a language has no Bible set up).
 */
data class DailyVerse(
    val reference: String = "",
    val text: String = "",
    val credit: String = "",
    val referenceNb: String = "",
    val textNb: String = "",
    val creditNb: String = "",
    val referenceOm: String = "",
    val textOm: String = "",
    val creditOm: String = "",
    // The version and the full copyright of each Bible (shown on the page "Bible versions and copyright").
    val versionEn: String = "",
    val copyrightEn: String = "",
    val versionNb: String = "",
    val copyrightNb: String = "",
    val versionOm: String = "",
    val copyrightOm: String = "",
    /** Where the verse is, as "PSA.89.1" (book, chapter, verse), so a tap can open the Bible there. */
    val usfm: String = ""
) {
    /** The verse in this language ("nb", "om", else English), or null when there is none in that language. */
    fun inLanguage(language: String): VerseLine? {
        val line = when (language) {
            "nb" -> VerseLine(textNb, referenceNb, creditNb)
            "om" -> VerseLine(textOm, referenceOm, creditOm)
            else -> VerseLine(text, reference, credit)
        }
        return line.takeIf { it.text.isNotBlank() }
    }
}

class VerseViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _verse = MutableStateFlow<DailyVerse?>(null)
    val verse: StateFlow<DailyVerse?> = _verse.asStateFlow()
    private val dailyRegistration: ListenerRegistration =
        db.collection("settings").document("verseOfDay").addSnapshotListener { snapshot, _ ->
            val daily = DailyVerse(
                reference = snapshot?.getString("reference").orEmpty(),
                text = snapshot?.getString("text").orEmpty(),
                credit = snapshot?.getString("creditEn").orEmpty(),
                referenceNb = snapshot?.getString("referenceNb").orEmpty(),
                textNb = snapshot?.getString("textNb").orEmpty(),
                creditNb = snapshot?.getString("creditNb").orEmpty(),
                referenceOm = snapshot?.getString("referenceOm").orEmpty(),
                textOm = snapshot?.getString("textOm").orEmpty(),
                creditOm = snapshot?.getString("creditOm").orEmpty(),
                versionEn = snapshot?.getString("versionEn").orEmpty(),
                copyrightEn = snapshot?.getString("copyrightEn").orEmpty(),
                versionNb = snapshot?.getString("versionNb").orEmpty(),
                copyrightNb = snapshot?.getString("copyrightNb").orEmpty(),
                versionOm = snapshot?.getString("versionOm").orEmpty(),
                copyrightOm = snapshot?.getString("copyrightOm").orEmpty(),
                usfm = snapshot?.getString("usfm").orEmpty()
            )
            val nothing = daily.text.isBlank() && daily.textNb.isBlank() && daily.textOm.isBlank() &&
                daily.versionEn.isBlank() && daily.versionNb.isBlank() && daily.versionOm.isBlank()
            _verse.value = if (nothing) null else daily
        }

    override fun onCleared() {
        dailyRegistration.remove()
    }
}
