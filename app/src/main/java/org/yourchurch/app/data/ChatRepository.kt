package org.yourchurch.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A raw account profile, before we know whether the person is approved. */
data class UserRow(val uid: String, val name: String, val email: String, val phone: String)

class ChatRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val rooms = db.collection("chatRooms")

    /** The title this person's messages carry: "admin" (owner), "leader", or empty. Set by the chat when it starts. */
    var senderRole: String = ""
    private val approved = db.collection("approvedMembers")
    private val users = db.collection("users")
    private val reports = db.collection("chatReports")

    private fun <T> queryFlow(query: Query, map: (DocumentSnapshot) -> T): Flow<Result<List<T>>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
            } else if (snapshot != null) {
                trySend(Result.success(snapshot.documents.map(map)))
            }
        }
        awaitClose { registration.remove() }
    }

    // ---------- Reading ----------

    /** Whether an admin has approved this person (live). */
    fun observeApproved(uid: String): Flow<Result<Boolean>> = callbackFlow {
        val registration = approved.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
            } else if (snapshot != null) {
                trySend(Result.success(snapshot.exists()))
            }
        }
        awaitClose { registration.remove() }
    }

    fun observeMyRooms(uid: String) =
        queryFlow(rooms.whereArrayContains("memberIds", uid)) { it.toRoom() }

    fun observeDirectory() =
        queryFlow(approved.limit(500)) { DirectoryMember(it.id, it.getString("name") ?: "") }

    fun observeMessages(roomId: String, limit: Long) =
        queryFlow(
            rooms.document(roomId).collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
        ) { it.toMessage() }

    // Admin only
    fun observeUsers() = queryFlow(users.limit(500)) {
        UserRow(it.id, it.getString("displayName") ?: "", it.getString("email") ?: "", it.getString("phone") ?: "")
    }

    fun observeReports() = queryFlow(
        reports.orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
    ) { it.toReport() }

    // ---------- Groups ----------

    suspend fun createRoom(
        name: String,
        description: String,
        open: Boolean,
        uid: String,
        myName: String,
        invited: Map<String, String>,
        channel: Boolean = false
    ): String {
        val members = LinkedHashMap<String, String>()
        members[uid] = myName
        invited.forEach { (id, memberName) -> members[id] = memberName }
        val data = hashMapOf<String, Any>(
            "name" to name,
            "description" to description,
            "open" to open,
            "createdBy" to uid,
            "createdByName" to myName,
            "createdAt" to FieldValue.serverTimestamp(),
            "memberIds" to members.keys.toList(),
            "memberNames" to members
        )
        if (channel) data["channel"] = true
        return withTimeout(15_000) { rooms.add(data).await().id }
    }

    /** Starts a private conversation between me and one other person. The id is the same whoever starts it. */
    suspend fun createDirect(roomId: String, uid: String, myName: String, otherUid: String, otherName: String) {
        val data = hashMapOf<String, Any>(
            "name" to "direct",
            "description" to "",
            "open" to false,
            "direct" to true,
            "createdBy" to uid,
            "createdByName" to myName,
            "createdAt" to FieldValue.serverTimestamp(),
            "memberIds" to listOf(uid, otherUid),
            "memberNames" to mapOf(uid to myName, otherUid to otherName)
        )
        withTimeout(15_000) { rooms.document(roomId).set(data).await() }
    }

    // ---------- Blocking and the private-message switch ----------

    /** The people I have blocked from sending me private messages. */
    fun observeBlocked(uid: String): Flow<Result<Set<String>>> = callbackFlow {
        val registration = users.document(uid).collection("blocked")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(Result.success(snapshot.documents.map { it.id }.toSet()))
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun block(uid: String, otherUid: String) {
        withTimeout(15_000) {
            users.document(uid).collection("blocked").document(otherUid)
                .set(mapOf("at" to FieldValue.serverTimestamp())).await()
        }
    }

    suspend fun unblock(uid: String, otherUid: String) {
        withTimeout(15_000) { users.document(uid).collection("blocked").document(otherUid).delete().await() }
    }

    /** The admin switch for private messaging. On unless an admin has turned it off. */
    fun observeDmEnabled(): Flow<Result<Boolean>> = callbackFlow {
        val registration = db.collection("settings").document("chat")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    trySend(Result.success(snapshot.getBoolean("dmEnabled") ?: true))
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun setDmEnabled(enabled: Boolean) {
        withTimeout(15_000) {
            db.collection("settings").document("chat").set(mapOf("dmEnabled" to enabled)).await()
        }
    }

    /** Used for leaving, and for an owner or admin removing someone. */
    suspend fun removeMember(roomId: String, uid: String) {
        withTimeout(15_000) {
            rooms.document(roomId).update(
                mapOf(
                    "memberIds" to FieldValue.arrayRemove(uid),
                    "memberNames.$uid" to FieldValue.delete()
                )
            ).await()
        }
        // Stop sharing their phone number with this group (best effort).
        runCatching { withTimeout(15_000) { rooms.document(roomId).collection("phones").document(uid).delete().await() } }
    }

    suspend fun addMembers(roomId: String, people: Map<String, String>) {
        if (people.isEmpty()) return
        val update = HashMap<String, Any>()
        update["memberIds"] = FieldValue.arrayUnion(*people.keys.toTypedArray())
        people.forEach { (uid, name) -> update["memberNames.$uid"] = name }
        withTimeout(15_000) { rooms.document(roomId).update(update).await() }
    }

    /** Deletes every message in the group, then the group itself. */
    suspend fun deleteRoom(roomId: String) {
        runCatching {
            val reads = withTimeout(20_000) { rooms.document(roomId).collection("reads").get().await() }
            if (!reads.isEmpty) {
                val batch = db.batch()
                reads.documents.forEach { batch.delete(it.reference) }
                withTimeout(20_000) { batch.commit().await() }
            }
        }
        runCatching {
            val phones = withTimeout(20_000) { rooms.document(roomId).collection("phones").get().await() }
            if (!phones.isEmpty) {
                val batch = db.batch()
                phones.documents.forEach { batch.delete(it.reference) }
                withTimeout(20_000) { batch.commit().await() }
            }
        }
        val messages = rooms.document(roomId).collection("messages")
        while (true) {
            val snapshot = withTimeout(20_000) { messages.limit(400).get().await() }
            if (snapshot.isEmpty) break
            val batch = db.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            withTimeout(20_000) { batch.commit().await() }
        }
        withTimeout(15_000) { rooms.document(roomId).delete().await() }
    }

    // ---------- Messages ----------

    /**
     * Sends a message. Does not wait for the server, so it feels instant and also works
     * offline (it is sent when the connection returns). [onFailure] is called if it is rejected.
     */
    fun sendMessage(
        room: ChatRoom,
        text: String,
        uid: String,
        name: String,
        reply: ChatMessage?,
        onFailure: () -> Unit
    ) {
        val messageRef = rooms.document(room.id).collection("messages").document()
        val data = hashMapOf<String, Any>(
            "text" to text,
            "senderId" to uid,
            "senderName" to name,
            "role" to senderRole,
            "createdAt" to FieldValue.serverTimestamp(),
            "deleted" to false
        )
        if (reply != null) {
            data["replyToId"] = reply.id
            data["replyToName"] = reply.senderName.take(60)
            data["replyToText"] = (if (reply.deleted) "" else reply.text.ifBlank { if (reply.imagePath.isNotBlank()) "\uD83D\uDCF7" else if (reply.audioPath.isNotBlank()) "\uD83C\uDFA4" else "" }).take(100)
        }
        val batch = db.batch()
        batch.set(messageRef, data)
        batch.update(
            rooms.document(room.id),
            mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessageText" to text.take(100),
                "lastMessageBy" to uid
            )
        )
        batch.commit().addOnFailureListener { onFailure() }
    }

    /** Posts a poll: the question, and two to six options that people can vote for. */
    fun sendPoll(room: ChatRoom, question: String, options: List<String>, uid: String, name: String, onFailure: () -> Unit) {
        val messageRef = rooms.document(room.id).collection("messages").document()
        val data = hashMapOf<String, Any>(
            "text" to question,
            "senderId" to uid,
            "senderName" to name,
            "role" to senderRole,
            "createdAt" to FieldValue.serverTimestamp(),
            "deleted" to false,
            "pollOptions" to options,
            "votes" to hashMapOf<String, Any>()
        )
        val batch = db.batch()
        batch.set(messageRef, data)
        batch.update(
            rooms.document(room.id),
            mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessageText" to ("\uD83D\uDCCA " + question).take(100),
                "lastMessageBy" to uid
            )
        )
        batch.commit().addOnFailureListener { onFailure() }
    }

    /** My vote in a poll (replacing an earlier one), or no vote when [option] is null. */
    suspend fun vote(roomId: String, messageId: String, uid: String, option: Int?) {
        val ref = rooms.document(roomId).collection("messages").document(messageId)
        withTimeout(15_000) { ref.update("votes.$uid", option ?: FieldValue.delete()).await() }
    }

    /** Sets my reaction on a message (replacing any earlier one of mine), or removes it when [emoji] is null. */
    suspend fun react(roomId: String, messageId: String, uid: String, emoji: String?) {
        val value: Any = emoji ?: FieldValue.delete()
        withTimeout(15_000) {
            rooms.document(roomId).collection("messages").document(messageId)
                .update(FieldPath.of("reactions", uid), value).await()
        }
    }

    /** Changes the text of my own message. The old text is kept in "history". */
    suspend fun editMessage(roomId: String, message: ChatMessage, newText: String) {
        withTimeout(15_000) {
            rooms.document(roomId).collection("messages").document(message.id).update(
                mapOf<String, Any>(
                    "text" to newText,
                    "edited" to true,
                    "editedAt" to FieldValue.serverTimestamp(),
                    "history" to historyWith(message.text, message.history)
                )
            ).await()
        }
    }

    suspend fun pin(roomId: String, message: ChatMessage) {
        withTimeout(15_000) {
            rooms.document(roomId).update(
                mapOf(
                    "pinnedMessageId" to message.id,
                    "pinnedText" to message.text.take(200),
                    "pinnedName" to message.senderName.take(60)
                )
            ).await()
        }
    }

    suspend fun unpin(roomId: String) {
        withTimeout(15_000) {
            rooms.document(roomId).update(
                mapOf(
                    "pinnedMessageId" to FieldValue.delete(),
                    "pinnedText" to FieldValue.delete(),
                    "pinnedName" to FieldValue.delete()
                )
            ).await()
        }
    }

    /** Anyone in the group can change its colour. */
    suspend fun setGroupColor(roomId: String, index: Int) {
        withTimeout(15_000) { rooms.document(roomId).update("color", index).await() }
    }

    /** Sends a photo (with an optional caption): the picture goes to private storage, then the message is added. */
    suspend fun sendPhoto(
        room: ChatRoom,
        uid: String,
        name: String,
        caption: String,
        reply: ChatMessage?,
        photo: ChatImages.Prepared
    ) {
        val messageRef = rooms.document(room.id).collection("messages").document()
        val path = "chat/${room.id}/${messageRef.id}.jpg"
        ChatImages.upload(path, photo.full)
        val data = hashMapOf<String, Any>(
            "text" to caption,
            "senderId" to uid,
            "senderName" to name,
            "role" to senderRole,
            "createdAt" to FieldValue.serverTimestamp(),
            "deleted" to false,
            "imagePath" to path,
            "thumb" to com.google.firebase.firestore.Blob.fromBytes(photo.thumb)
        )
        if (reply != null) {
            data["replyToId"] = reply.id
            data["replyToName"] = reply.senderName.take(60)
            data["replyToText"] = (if (reply.deleted) "" else reply.text.ifBlank { if (reply.imagePath.isNotBlank()) "\uD83D\uDCF7" else if (reply.audioPath.isNotBlank()) "\uD83C\uDFA4" else "" }).take(100)
        }
        val batch = db.batch()
        batch.set(messageRef, data)
        batch.update(
            rooms.document(room.id),
            mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessageText" to ("\uD83D\uDCF7 " + caption).trim().take(100),
                "lastMessageBy" to uid
            )
        )
        try {
            withTimeout(20_000) { batch.commit().await() }
        } catch (e: Exception) {
            ChatImages.deleteFile(path)
            throw e
        }
    }

    /** Removes a message. If it was a photo, the picture is deleted from storage too. */
    suspend fun removeMessage(roomId: String, message: ChatMessage) {
        val update = hashMapOf<String, Any>("deleted" to true, "text" to "")
        if (message.imagePath.isNotBlank()) {
            update["imagePath"] = FieldValue.delete()
            update["thumb"] = FieldValue.delete()
        }
        if (message.audioPath.isNotBlank()) {
            update["audioPath"] = FieldValue.delete()
            update["audioMs"] = FieldValue.delete()
        }
        withTimeout(15_000) {
            rooms.document(roomId).collection("messages").document(message.id).update(update).await()
        }
        if (message.imagePath.isNotBlank()) ChatImages.deleteFile(message.imagePath)
        if (message.audioPath.isNotBlank()) ChatImages.deleteFile(message.audioPath)
    }

    /** Sends a voice message: the recording goes to private storage, then the message is added. */
    suspend fun sendVoice(
        room: ChatRoom,
        uid: String,
        name: String,
        file: java.io.File,
        durationMs: Int,
        reply: ChatMessage?
    ) {
        val messageRef = rooms.document(room.id).collection("messages").document()
        val path = "chat/${room.id}/${messageRef.id}.m4a"
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        ChatImages.uploadAudio(path, bytes)
        val data = hashMapOf<String, Any>(
            "text" to "",
            "senderId" to uid,
            "senderName" to name,
            "role" to senderRole,
            "createdAt" to FieldValue.serverTimestamp(),
            "deleted" to false,
            "audioPath" to path,
            "audioMs" to durationMs
        )
        if (reply != null) {
            data["replyToId"] = reply.id
            data["replyToName"] = reply.senderName.take(60)
            data["replyToText"] = (if (reply.deleted) "" else reply.text.ifBlank { if (reply.imagePath.isNotBlank()) "\uD83D\uDCF7" else if (reply.audioPath.isNotBlank()) "\uD83C\uDFA4" else "" }).take(100)
        }
        val seconds = durationMs / 1000
        val batch = db.batch()
        batch.set(messageRef, data)
        batch.update(
            rooms.document(room.id),
            mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessageText" to ("\uD83C\uDFA4 " + (seconds / 60) + ":" + String.format("%02d", seconds % 60)),
                "lastMessageBy" to uid
            )
        )
        try {
            withTimeout(20_000) { batch.commit().await() }
        } catch (e: Exception) {
            ChatImages.deleteFile(path)
            throw e
        }
    }

    // ---------- Muted conversations (no notifications; kept with my notification settings) ----------

    fun observeMuted(uid: String): Flow<Result<Set<String>>> = callbackFlow {
        val registration = db.collection("fcmTokens").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    val muted = (snapshot.get("muted") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                    trySend(Result.success(muted))
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun setMuted(uid: String, roomId: String, muted: Boolean) {
        withTimeout(15_000) {
            db.collection("fcmTokens").document(uid)
                .set(
                    mapOf("muted" to (if (muted) FieldValue.arrayUnion(roomId) else FieldValue.arrayRemove(roomId))),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
        }
    }

    suspend fun removeMessage(roomId: String, messageId: String) {
        withTimeout(15_000) {
            rooms.document(roomId).collection("messages").document(messageId)
                .update(mapOf("deleted" to true, "text" to "")).await()
        }
    }

    suspend fun report(room: ChatRoom, message: ChatMessage, reporterId: String, reporterName: String) {
        val data = hashMapOf<String, Any>(
            "roomId" to room.id,
            "roomName" to (if (room.direct) "Private message" else room.name).take(60),
            "messageId" to message.id,
            "messageText" to message.text.take(2000),
            "senderId" to message.senderId,
            "senderName" to message.senderName.take(60),
            "reporterId" to reporterId,
            "reporterName" to reporterName.take(60),
            "createdAt" to FieldValue.serverTimestamp()
        )
        withTimeout(15_000) { reports.add(data).await() }
    }

    // ---------- Read receipts ----------

    /** When each person in this conversation last opened it: user id -> time. */
    fun observeReads(roomId: String): Flow<Result<Map<String, Date>>> = callbackFlow {
        val registration = rooms.document(roomId).collection("reads")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    val map = HashMap<String, Date>()
                    snapshot.documents.forEach { doc ->
                        doc.getTimestamp("at", ServerTimestampBehavior.ESTIMATE)?.toDate()?.let { map[doc.id] = it }
                    }
                    trySend(Result.success(map))
                }
            }
        awaitClose { registration.remove() }
    }

    /** Records that I have now seen everything in this conversation. */
    suspend fun markReadRemote(roomId: String, uid: String) {
        withTimeout(15_000) {
            rooms.document(roomId).collection("reads").document(uid)
                .set(mapOf("at" to FieldValue.serverTimestamp())).await()
        }
    }

    // ---------- Shared phone numbers (opt-in, per group) ----------

    /**
     * The numbers shared with me in this group. A group owner or admin (a "moderator") sees every number
     * people shared with the leader or the group. Everyone else only sees numbers shared with the whole group.
     */
    fun observeRoomPhones(roomId: String, moderator: Boolean): Flow<Result<Map<String, String>>> = callbackFlow {
        val phones = rooms.document(roomId).collection("phones")
        val query: Query = if (moderator) phones else phones.whereEqualTo("audience", "group")
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
            } else if (snapshot != null) {
                trySend(Result.success(snapshot.documents.associate { it.id to (it.getString("phone") ?: "") }))
            }
        }
        awaitClose { registration.remove() }
    }

    /** Who I am sharing my number with in this group: "" (nobody), "leaders" or "group". */
    fun observeMyShare(roomId: String, uid: String): Flow<Result<String>> = callbackFlow {
        val registration = rooms.document(roomId).collection("phones").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                } else if (snapshot != null) {
                    val audience = if (snapshot.exists()) (snapshot.getString("audience") ?: "leaders") else ""
                    trySend(Result.success(audience))
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun sharePhone(roomId: String, uid: String, phone: String, audience: String) {
        withTimeout(15_000) {
            rooms.document(roomId).collection("phones").document(uid)
                .set(mapOf("phone" to phone, "audience" to audience)).await()
        }
    }

    suspend fun stopSharingPhone(roomId: String, uid: String) {
        withTimeout(15_000) {
            rooms.document(roomId).collection("phones").document(uid).delete().await()
        }
    }

    // ---------- Admin ----------

    suspend fun approve(uid: String, name: String, adminUid: String) {
        val data = hashMapOf<String, Any>(
            "name" to name.take(60),
            "approvedAt" to FieldValue.serverTimestamp(),
            "approvedBy" to adminUid
        )
        withTimeout(15_000) { approved.document(uid).set(data).await() }
    }

    suspend fun revoke(uid: String) {
        withTimeout(15_000) { approved.document(uid).delete().await() }
    }

    suspend fun dismissReport(reportId: String) {
        withTimeout(15_000) { reports.document(reportId).delete().await() }
    }

    // ---------- Mapping ----------

    private fun DocumentSnapshot.toRoom() = ChatRoom(
        id = id,
        name = getString("name") ?: "",
        description = getString("description") ?: "",
        open = getBoolean("open") ?: true,
        direct = getBoolean("direct") ?: false,
        createdBy = getString("createdBy") ?: "",
        createdByName = getString("createdByName") ?: "",
        createdAt = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
        memberIds = (get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        memberNames = (get("memberNames") as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap(),
        lastMessageAt = getTimestamp("lastMessageAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
        lastMessageText = getString("lastMessageText") ?: "",
        lastMessageBy = getString("lastMessageBy") ?: "",
        pinnedMessageId = getString("pinnedMessageId") ?: "",
        pinnedText = getString("pinnedText") ?: "",
        pinnedName = getString("pinnedName") ?: "",
        color = (getLong("color") ?: 0L).toInt(),
        channel = getBoolean("channel") ?: false
    )

    private fun DocumentSnapshot.toMessage() = ChatMessage(
        id = id,
        text = getString("text") ?: "",
        senderId = getString("senderId") ?: "",
        senderName = getString("senderName") ?: "",
        role = getString("role") ?: "",
        createdAt = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
        deleted = getBoolean("deleted") ?: false,
        kind = getString("kind") ?: "text",
        callRoom = getString("callRoom") ?: "",
        callEnded = getBoolean("callEnded") ?: false,
        // "Sending" only while the message itself has not reached the server (its server time is still missing).
        pending = metadata.hasPendingWrites() && getTimestamp("createdAt") == null,
        replyToId = getString("replyToId") ?: "",
        replyToName = getString("replyToName") ?: "",
        replyToText = getString("replyToText") ?: "",
        reactions = (get("reactions") as? Map<*, *>)?.entries
            ?.mapNotNull { entry -> (entry.value as? String)?.let { entry.key.toString() to it } }
            ?.toMap() ?: emptyMap(),
        edited = getBoolean("edited") ?: false,
        history = parseHistory(get("history")),
        imagePath = getString("imagePath") ?: "",
        thumb = getBlob("thumb")?.toBytes(),
        audioPath = getString("audioPath") ?: "",
        audioMs = (getLong("audioMs") ?: 0L).toInt(),
        pollOptions = (get("pollOptions") as? List<*>)?.map { it.toString() } ?: emptyList(),
        votes = (get("votes") as? Map<*, *>)?.entries
            ?.mapNotNull { entry -> (entry.value as? Number)?.let { entry.key.toString() to it.toInt() } }
            ?.toMap() ?: emptyMap()
    )

    private fun DocumentSnapshot.toReport() = ChatReport(
        id = id,
        roomId = getString("roomId") ?: "",
        roomName = getString("roomName") ?: "",
        messageId = getString("messageId") ?: "",
        messageText = getString("messageText") ?: "",
        senderId = getString("senderId") ?: "",
        senderName = getString("senderName") ?: "",
        reporterId = getString("reporterId") ?: "",
        reporterName = getString("reporterName") ?: "",
        createdAt = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate()
    )
}
