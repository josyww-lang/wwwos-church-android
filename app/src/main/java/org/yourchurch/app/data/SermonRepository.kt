package org.yourchurch.app.data

import android.net.Uri
import androidx.compose.runtime.getValue
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

sealed interface UploadEvent {
    data class Progress(val fraction: Float) : UploadEvent
    data class Done(val path: String) : UploadEvent
    data class Failed(val error: Throwable) : UploadEvent
}

class SermonRepository(db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val col = db.collection("sermons")

    // Created only when first needed, so a missing storage setup can't break the sermon list.
    private val storage by lazy { FirebaseStorage.getInstance() }

    /** The 60 newest sermons, live. */
    fun observe(): Flow<Result<List<Sermon>>> = callbackFlow {
        val registration = col.orderBy("date", Query.Direction.DESCENDING).limit(60)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(Result.success(snapshot.documents.map { it.toSermon() }))
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun save(s: Sermon) {
        val data = hashMapOf<String, Any>(
            "title" to s.title,
            "speaker" to s.speaker,
            "date" to Timestamp(s.date),
            "language" to s.language,
            "series" to s.series,
            "description" to s.description,
            "youtubeId" to s.youtubeId,
            "audioPath" to s.audioPath,
            "audioUrl" to s.audioUrl,
            "published" to s.published,
            "createdAt" to Timestamp(s.createdAt),
            "authorId" to s.authorId
        )
        withTimeout(15_000) {
            // Editing keeps the reactions and anything else already saved on the sermon.
            if (s.id.isEmpty()) col.add(data).await() else col.document(s.id).set(data, SetOptions.merge()).await()
        }
    }

    /** Deletes the sermon, then its audio file (best effort). */
    suspend fun delete(s: Sermon) {
        withTimeout(15_000) { col.document(s.id).delete().await() }
        if (s.audioPath.isNotEmpty()) deleteAudio(s.audioPath)
    }

    // ---------- Reactions and comments ----------

    /** Add or remove my reaction on a sermon. */
    suspend fun setReaction(sermonId: String, emoji: String, uid: String, add: Boolean) {
        val change = if (add) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
        withTimeout(15_000) { col.document(sermonId).update(FieldPath.of("reactions", emoji), change).await() }
    }

    fun observeComments(sermonId: String): Flow<Result<List<SermonComment>>> = callbackFlow {
        val registration = col.document(sermonId).collection("comments")
            .orderBy("createdAt").limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(Result.success(snapshot.documents.map { it.toComment() }))
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun addComment(sermonId: String, text: String, uid: String, name: String, reply: SermonComment?) {
        val data = hashMapOf<String, Any>(
            "text" to text,
            "authorId" to uid,
            "authorName" to name.take(60),
            "createdAt" to FieldValue.serverTimestamp(),
            "likes" to emptyList<String>()
        )
        if (reply != null) {
            data["replyToId"] = reply.id.take(64)
            data["replyToName"] = reply.authorName.take(60)
            data["replyToText"] = reply.text.take(150)
        }
        withTimeout(15_000) { col.document(sermonId).collection("comments").add(data).await() }
    }

    suspend fun setCommentLike(sermonId: String, commentId: String, uid: String, add: Boolean) {
        val change = if (add) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
        withTimeout(15_000) {
            col.document(sermonId).collection("comments").document(commentId).update("likes", change).await()
        }
    }

    suspend fun deleteComment(sermonId: String, commentId: String) {
        withTimeout(15_000) { col.document(sermonId).collection("comments").document(commentId).delete().await() }
    }

    private fun DocumentSnapshot.toComment() = SermonComment(
        id = id,
        text = getString("text") ?: "",
        authorId = getString("authorId") ?: "",
        authorName = getString("authorName") ?: "",
        createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate(),
        likes = (get("likes") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        replyToName = getString("replyToName") ?: "",
        replyToText = getString("replyToText") ?: ""
    )

    suspend fun deleteAudio(path: String) {
        runCatching { withTimeout(15_000) { storage.reference.child(path).delete().await() } }
    }

    /** Uploads an audio file and reports progress. Cancelling the collector cancels the upload. */
    fun upload(uri: Uri, mime: String): Flow<UploadEvent> = callbackFlow {
        val extension = when (mime) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/3gpp" -> "3gp"
            "audio/amr" -> "amr"
            "audio/webm" -> "weba"
            "audio/x-ms-wma" -> "wma"
            "audio/aiff", "audio/x-aiff" -> "aiff"
            "audio/x-matroska" -> "mka"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> "audio"
        }
        val path = "sermons/${UUID.randomUUID()}.$extension"
        val task = try {
            storage.reference.child(path)
                .putFile(uri, StorageMetadata.Builder().setContentType(mime).build())
        } catch (e: Exception) {
            trySend(UploadEvent.Failed(e))
            close()
            return@callbackFlow
        }
        task.addOnProgressListener { snap ->
            if (snap.totalByteCount > 0) {
                trySend(UploadEvent.Progress(snap.bytesTransferred.toFloat() / snap.totalByteCount))
            }
        }
        task.addOnSuccessListener {
            trySend(UploadEvent.Done(path))
            close()
        }
        task.addOnFailureListener {
            trySend(UploadEvent.Failed(it))
            close()
        }
        awaitClose { if (!task.isComplete) task.cancel() }
    }

    private fun DocumentSnapshot.toSermon() = Sermon(
        id = id,
        title = getString("title") ?: "",
        speaker = getString("speaker") ?: "",
        date = getTimestamp("date")?.toDate() ?: Date(),
        language = getString("language") ?: "en",
        series = getString("series") ?: "",
        description = getString("description") ?: "",
        youtubeId = getString("youtubeId") ?: "",
        audioPath = getString("audioPath") ?: "",
        audioUrl = getString("audioUrl") ?: "",
        published = getBoolean("published") ?: true,
        createdAt = getTimestamp("createdAt")?.toDate() ?: Date(0),
        authorId = getString("authorId") ?: "",
        reactions = (get("reactions") as? Map<*, *>)?.entries?.associate { entry ->
            entry.key.toString() to ((entry.value as? List<*>)?.filterIsInstance<String>() ?: emptyList())
        } ?: emptyMap()
    )
}
