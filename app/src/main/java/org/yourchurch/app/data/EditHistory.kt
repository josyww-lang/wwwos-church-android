package org.yourchurch.app.data

import com.google.firebase.Timestamp
import java.util.Date

/** An earlier version of a text that was edited, and when it was replaced. */
data class EditVersion(val text: String, val at: Date?) {
    fun toMap(): Map<String, Any> = mapOf("text" to text, "at" to Timestamp(at ?: Date()))
}

fun parseHistory(raw: Any?): List<EditVersion> =
    (raw as? List<*>)?.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        EditVersion(
            text = map["text"] as? String ?: "",
            at = (map["at"] as? Timestamp)?.toDate()
        )
    } ?: emptyList()

/** The saved history with the text that was just replaced added at the end. At most 20 versions are kept. */
fun historyWith(previousText: String, existing: List<EditVersion>): List<Map<String, Any>> =
    existing.takeLast(19).map { it.toMap() } + EditVersion(previousText.take(4000), Date()).toMap()
