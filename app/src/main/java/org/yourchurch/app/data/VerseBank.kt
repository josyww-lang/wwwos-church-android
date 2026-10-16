package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** One day of the verse list: the verse in Afaan Oromoo, Norsk and English (each with its own reference). */
data class BankVerse(
    val day: Int = 0,
    val refOm: String = "",
    val textOm: String = "",
    val refNb: String = "",
    val textNb: String = "",
    val refEn: String = "",
    val textEn: String = ""
) {
    /** The text and the reference in this language ("om", "nb", else English). Falls back to English, or null if empty. */
    fun forLanguage(language: String): Pair<String, String>? {
        val (text, ref) = when (language) {
            "om" -> textOm to refOm
            "nb" -> textNb to refNb
            else -> textEn to refEn
        }
        if (text.isNotBlank()) return text to ref
        if (textEn.isNotBlank()) return textEn to refEn
        return null
    }
}

const val MAX_VERSE_DAYS = 366

fun DocumentSnapshot.toBankVerse() = BankVerse(
    day = (getLong("day") ?: id.toLongOrNull() ?: 0L).toInt(),
    refOm = getString("refOm").orEmpty(),
    textOm = getString("textOm").orEmpty(),
    refNb = getString("refNb").orEmpty(),
    textNb = getString("textNb").orEmpty(),
    refEn = getString("refEn").orEmpty(),
    textEn = getString("textEn").orEmpty()
)

private fun BankVerse.toMap(): Map<String, Any> = mapOf(
    "day" to day,
    "refOm" to refOm.take(60), "textOm" to textOm.take(800),
    "refNb" to refNb.take(60), "textNb" to textNb.take(800),
    "refEn" to refEn.take(60), "textEn" to textEn.take(800)
)

/**
 * Reads rows copied from the spreadsheet (cells separated by tabs): Day, suggested verse (ignored), then reference and
 * text in Afaan Oromoo, in Norsk and in English.
 */
fun parseBankRows(pasted: String): List<BankVerse> {
    fun clean(cell: String?): String {
        var s = cell.orEmpty().trim()
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1).replace("\"\"", "\"")
        return s.trim()
    }
    return pasted.lines().mapNotNull { line ->
        val cells = line.split('\t')
        val day = clean(cells.getOrNull(0)).toIntOrNull() ?: return@mapNotNull null
        if (day < 1 || day > MAX_VERSE_DAYS) return@mapNotNull null
        val verse = BankVerse(
            day = day,
            refOm = clean(cells.getOrNull(2)), textOm = clean(cells.getOrNull(3)),
            refNb = clean(cells.getOrNull(4)), textNb = clean(cells.getOrNull(5)),
            refEn = clean(cells.getOrNull(6)).ifBlank { clean(cells.getOrNull(1)) }, textEn = clean(cells.getOrNull(7))
        )
        if (verse.textOm.isBlank() && verse.textNb.isBlank() && verse.textEn.isBlank()) null else verse
    }
}

/** For leaders: the list of verses (one for each day; after the last day it starts again from day 1). */
class VerseBankViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val col = db.collection("verseBank")
    private val meta = db.collection("settings").document("verseBank")
    private val _verses = MutableStateFlow<List<BankVerse>>(emptyList())
    val verses: StateFlow<List<BankVerse>> = _verses.asStateFlow()
    private val registration: ListenerRegistration = col.addSnapshotListener { snapshot, error ->
        if (error != null || snapshot == null) return@addSnapshotListener
        _verses.value = snapshot.documents.map { it.toBankVerse() }.filter { it.day > 0 }.sortedBy { it.day }
    }

    /** Saves some days at once (a day that is already there is replaced) and updates how many days the list has. */
    suspend fun save(rows: List<BankVerse>): Result<Int> = runCatching {
        withTimeout(60_000) {
            rows.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.set(col.document(it.day.toString()), it.toMap()) }
                batch.commit().await()
            }
            val count = maxOf(_verses.value.maxOfOrNull { it.day } ?: 0, rows.maxOfOrNull { it.day } ?: 0)
            meta.set(mapOf("count" to count, "updatedAt" to Timestamp.now())).await()
        }
        rows.size
    }

    suspend fun delete(day: Int): Result<Unit> = runCatching {
        withTimeout(20_000) {
            col.document(day.toString()).delete().await()
            val remaining = _verses.value.filter { it.day != day }.maxOfOrNull { it.day } ?: 0
            meta.set(mapOf("count" to remaining, "updatedAt" to Timestamp.now())).await()
        }
    }

    override fun onCleared() {
        registration.remove()
    }
}
