package org.yourchurch.app.data

import androidx.annotation.StringRes
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.yourchurch.app.R

enum class Category(val id: String, @StringRes val labelRes: Int) {
    Service("service", R.string.cat_service),
    Event("event", R.string.cat_event),
    Prayer("prayer", R.string.cat_prayer),
    Community("community", R.string.cat_community),
    Urgent("urgent", R.string.cat_urgent);

    companion object {
        fun from(id: String): Category = entries.firstOrNull { it.id == id } ?: Community
    }
}

/**
 * One document in the Firestore "announcements" collection.
 * [date] is the day the announcement is for (start of that day).
 */
data class Announcement(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val category: String = Category.Community.id,
    val date: Date = Date(),
    val pinned: Boolean = false,
    val createdAt: Date = Date(0),
    val authorId: String = "",
    val edited: Boolean = false,
    val history: List<EditVersion> = emptyList(),
    /** When set, the announcement appears (and members are notified) at this time, not before. */
    val publishAt: Date? = null,
    /** An optional time of day, like "18:00" (empty = no time). */
    val time: String = ""
)

fun Date.toLocalDate(): LocalDate = toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.toStartOfDayDate(): Date =
    Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())
