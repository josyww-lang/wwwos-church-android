package org.yourchurch.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class AnnouncementRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val col = db.collection("announcements")

    /**
     * Live list of announcements from [start] (inclusive) to [end] (exclusive).
     * If [end] is null, returns the next 50 announcements from [start] onward.
     */
    fun observe(start: Date, end: Date?): Flow<Result<List<Announcement>>> = callbackFlow {
        var query: Query = col.whereGreaterThanOrEqualTo("date", Timestamp(start))
        query = if (end != null) {
            query.whereLessThan("date", Timestamp(end))
        } else {
            query.orderBy("date").limit(50)
        }
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
            } else if (snapshot != null) {
                trySend(Result.success(snapshot.documents.map { it.toAnnouncement() }))
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun save(a: Announcement) {
        val data = hashMapOf<String, Any>(
            "title" to a.title,
            "message" to a.message,
            "category" to a.category,
            "date" to Timestamp(a.date),
            "time" to a.time,
            "pinned" to a.pinned,
            "createdAt" to Timestamp(a.createdAt),
            "authorId" to a.authorId
        )
        a.publishAt?.let { data["publishAt"] = Timestamp(it) }
        // When an existing announcement is changed, keep the earlier wording so people can see it.
        if (a.id.isNotEmpty()) {
            val before = withTimeout(15_000) { col.document(a.id).get().await() }
            val oldTitle = before.getString("title") ?: ""
            val oldMessage = before.getString("message") ?: ""
            val oldHistory = parseHistory(before.get("history"))
            // Keep the "already announced" mark, unless the publishing time was moved.
            if (before.getBoolean("notified") == true && before.getTimestamp("publishAt")?.toDate()?.time == a.publishAt?.time) {
                data["notified"] = true
            }
            if (oldTitle != a.title || oldMessage != a.message) {
                data["edited"] = true
                data["editedAt"] = Timestamp.now()
                data["history"] = historyWith(if (oldMessage.isBlank()) oldTitle else "$oldTitle\n\n$oldMessage", oldHistory)
            } else if (before.getBoolean("edited") == true) {
                data["edited"] = true
                before.getTimestamp("editedAt")?.let { data["editedAt"] = it }
                data["history"] = oldHistory.map { it.toMap() }
            }
        }
        withTimeout(15_000) {
            if (a.id.isEmpty()) col.add(data).await() else col.document(a.id).set(data).await()
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        withTimeout(15_000) { col.document(id).update("pinned", pinned).await() }
    }

    suspend fun delete(id: String) {
        withTimeout(15_000) { col.document(id).delete().await() }
    }

    private fun DocumentSnapshot.toAnnouncement() = Announcement(
        id = id,
        title = getString("title") ?: "Untitled",
        message = getString("message") ?: "",
        category = getString("category") ?: Category.Community.id,
        date = getTimestamp("date")?.toDate() ?: Date(),
        pinned = getBoolean("pinned") ?: false,
        createdAt = getTimestamp("createdAt")?.toDate() ?: Date(0),
        authorId = getString("authorId") ?: "",
        edited = getBoolean("edited") ?: false,
        history = parseHistory(get("history")),
        publishAt = getTimestamp("publishAt")?.toDate(),
        time = getString("time") ?: ""
    )
}
