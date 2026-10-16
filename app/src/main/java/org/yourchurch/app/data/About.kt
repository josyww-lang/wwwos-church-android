package org.yourchurch.app.data

import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** One part of "About the church" (for example "Our history"), with a title and a text in three languages. */
data class AboutSection(
    val id: String = "",
    val order: Int = 0,
    val titleEn: String = "", val titleNb: String = "", val titleOm: String = "",
    val bodyEn: String = "", val bodyNb: String = "", val bodyOm: String = ""
) {
    /** The text in the person's language, or another language if that one is empty. */
    private fun pick(language: String, en: String, nb: String, om: String): String {
        val first = when (language) { "om" -> om; "nb" -> nb; else -> en }
        return first.ifBlank { en.ifBlank { nb.ifBlank { om } } }
    }

    fun titleFor(language: String) = pick(language, titleEn, titleNb, titleOm)
    fun bodyFor(language: String) = pick(language, bodyEn, bodyNb, bodyOm)
}

class AboutViewModel : ViewModel() {
    private val col = FirebaseFirestore.getInstance().collection("aboutSections")
    private val _sections = MutableStateFlow<List<AboutSection>>(emptyList())
    val sections: StateFlow<List<AboutSection>> = _sections.asStateFlow()
    private val registration: ListenerRegistration = col.orderBy("order").addSnapshotListener { snapshot, error ->
        if (error != null || snapshot == null) return@addSnapshotListener
        _sections.value = snapshot.documents.map { d ->
            AboutSection(
                id = d.id,
                order = (d.getLong("order") ?: 0L).toInt(),
                titleEn = d.getString("titleEn").orEmpty(), titleNb = d.getString("titleNb").orEmpty(), titleOm = d.getString("titleOm").orEmpty(),
                bodyEn = d.getString("bodyEn").orEmpty(), bodyNb = d.getString("bodyNb").orEmpty(), bodyOm = d.getString("bodyOm").orEmpty()
            )
        }
    }

    suspend fun save(s: AboutSection): Result<Unit> = runCatching {
        val doc = if (s.id.isBlank()) col.document() else col.document(s.id)
        val data = mapOf(
            "order" to s.order,
            "titleEn" to s.titleEn.take(120), "titleNb" to s.titleNb.take(120), "titleOm" to s.titleOm.take(120),
            "bodyEn" to s.bodyEn.take(15000), "bodyNb" to s.bodyNb.take(15000), "bodyOm" to s.bodyOm.take(15000),
            "updatedAt" to Timestamp.now()
        )
        withTimeout(15_000) { doc.set(data).await() }
    }

    suspend fun delete(id: String): Result<Unit> = runCatching { withTimeout(15_000) { col.document(id).delete().await() } }

    /** Moves a section up (-1) or down (+1) by swapping places with its neighbour. */
    suspend fun move(section: AboutSection, delta: Int): Result<Unit> = runCatching {
        val list = _sections.value.toMutableList()
        val from = list.indexOfFirst { it.id == section.id }
        val to = from + delta
        if (from < 0 || to < 0 || to >= list.size) return@runCatching
        list.add(to, list.removeAt(from))
        withTimeout(15_000) {
            val batch = FirebaseFirestore.getInstance().batch()
            list.forEachIndexed { index, item -> if (item.order != index) batch.update(col.document(item.id), "order", index) }
            batch.commit().await()
        }
    }

    override fun onCleared() {
        registration.remove()
    }
}
