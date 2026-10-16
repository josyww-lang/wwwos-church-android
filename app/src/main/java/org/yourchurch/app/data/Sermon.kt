package org.yourchurch.app.data

import androidx.annotation.StringRes
import java.net.URI
import java.util.Date
import org.yourchurch.app.R

/** One document in the Firestore "sermons" collection. */
data class Sermon(
    val id: String = "",
    val title: String = "",
    val speaker: String = "",
    val date: Date = Date(),
    /** "om" (Afaan Oromoo), "nb" (Norsk) or "en" (English) */
    val language: String = "en",
    val series: String = "",
    val description: String = "",
    /** The 11-character YouTube video ID, or empty. */
    val youtubeId: String = "",
    /** Path of an uploaded audio file in Firebase Storage (sermons/...), or empty. */
    val audioPath: String = "",
    /** An optional external audio link (https://...), or empty. */
    val audioUrl: String = "",
    /** Drafts are hidden from the list for members. */
    val published: Boolean = true,
    val createdAt: Date = Date(0),
    val authorId: String = "",
    /** Reactions: emoji -> ids of the people who chose it. */
    val reactions: Map<String, List<String>> = emptyMap()
) {
    val hasAudio: Boolean get() = audioPath.isNotEmpty() || audioUrl.isNotEmpty()
    val hasVideo: Boolean get() = youtubeId.isNotEmpty()
}

val SERMON_LANGUAGES = listOf("om", "nb", "en")

@StringRes
fun sermonLanguageLabel(code: String): Int = when (code) {
    "om" -> R.string.lang_om
    "nb" -> R.string.lang_nb
    else -> R.string.lang_en
}

private val youtubeIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Finds the video ID in a pasted YouTube link (watch, youtu.be, shorts, live, embed)
 * or accepts a bare 11-character ID. Returns null if it can't find one.
 */
fun extractYoutubeId(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return null
    if (youtubeIdPattern.matches(text)) return text

    val uri = runCatching { URI(if (text.startsWith("http")) text else "https://$text") }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
    val candidate: String? = when (host) {
        "youtu.be" -> uri.path?.trim('/')?.substringBefore('/')
        "youtube.com", "music.youtube.com" -> {
            val segments = uri.path.orEmpty().trim('/').split('/')
            if (segments.firstOrNull() in setOf("shorts", "live", "embed", "v")) {
                segments.getOrNull(1)
            } else {
                uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            }
        }
        else -> null
    }
    return candidate?.takeIf { youtubeIdPattern.matches(it) }
}

/** The reactions people can choose on a sermon (the same ones as in chat). The database rules allow exactly these. */
val SERMON_REACTIONS = listOf("\uD83D\uDC4D", "\uD83D\uDE4F", "\uD83D\uDC99", "\uD83D\uDE4C", "\uD83D\uDE0A", "\uD83D\uDE22")

/** A comment under a sermon. */
data class SermonComment(
    val id: String = "",
    val text: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val createdAt: java.util.Date? = null,
    /** Ids of the people who liked it. */
    val likes: List<String> = emptyList(),
    /** If it is a reply: who wrote the comment it answers, and a short copy of it. */
    val replyToName: String = "",
    val replyToText: String = ""
)
