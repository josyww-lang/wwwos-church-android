package org.yourchurch.app.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.util.Date

/** One chat group (a document in the Firestore "chatRooms" collection). */
data class ChatRoom(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    /** Open groups can be found and joined by any approved member. Invite-only ones cannot. */
    val open: Boolean = true,
    /** A private one-to-one conversation between two people (not a group). */
    val direct: Boolean = false,
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Date? = null,
    val memberIds: List<String> = emptyList(),
    /** uid -> display name, for showing who is in the group. */
    val memberNames: Map<String, String> = emptyMap(),
    val lastMessageAt: Date? = null,
    val lastMessageText: String = "",
    val lastMessageBy: String = "",
    /** The message pinned at the top of the group (only owners and admins can pin). */
    val pinnedMessageId: String = "",
    val pinnedText: String = "",
    val pinnedName: String = "",
    /** The group's colour: 0 follows the app's colour, 1 and up pick one of the group colours (see GroupColors.kt). */
    val color: Int = 0,
    /** An announcement channel: everyone in it can read, only leaders can post. */
    val channel: Boolean = false
) {
    val lastActivity: Date? get() = lastMessageAt ?: createdAt
}

data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    /** The sender's title when the message was sent: "admin", "leader", or empty. */
    val role: String = "",
    val createdAt: Date? = null,
    val deleted: Boolean = false,
    /** "text" for normal messages, "call" for a voice call card. */
    val kind: String = "text",
    /** For calls: the (random, hard to guess) name of the call room. */
    val callRoom: String = "",
    val callEnded: Boolean = false,
    /** True while the message is still being sent (not yet confirmed by the server). */
    val pending: Boolean = false,
    /** If this message is a reply: which message it answers (a short copy of it). */
    val replyToId: String = "",
    val replyToName: String = "",
    val replyToText: String = "",
    /** emoji -> people who reacted with it */
    val reactions: Map<String, String> = emptyMap(),
    /** True once the sender has changed the text. The earlier versions are in [history]. */
    val edited: Boolean = false,
    val history: List<EditVersion> = emptyList(),
    /** A photo: where the full picture is kept (private storage), and a small preview kept with the message. */
    val imagePath: String = "",
    val thumb: ByteArray? = null,
    /** A voice message: where the recording is kept (private storage) and how long it is. */
    val audioPath: String = "",
    val audioMs: Int = 0,
    /** A poll: the question is the message text; [votes] maps each person to the option (0, 1, ...) they chose. */
    val pollOptions: List<String> = emptyList(),
    val votes: Map<String, Int> = emptyMap()
)

/** A person in the approved-members directory. */
data class DirectoryMember(val uid: String, val name: String)

/** A person who has an account, with whether an admin has approved them for chat. */
data class PersonRow(val uid: String, val name: String, val email: String, val phone: String, val approved: Boolean)

data class ChatReport(
    val id: String = "",
    val roomId: String = "",
    val roomName: String = "",
    val messageId: String = "",
    val messageText: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val reporterId: String = "",
    val reporterName: String = "",
    val createdAt: Date? = null
)

/** Remembers, on this phone only, when each group was last opened (used for the "unread" dot). */
object ChatReadStore {
    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Kept per person, so two accounts on one phone don't share their "read" marks.
    fun lastRead(context: Context, uid: String, roomId: String): Long =
        prefs(context).getLong("chat_read_${uid}_$roomId", 0L)

    /** Changes every time something is marked read, so screens showing unread badges refresh. */
    private val versionState = mutableIntStateOf(0)
    val version: Int get() = versionState.intValue

    /** [atLeast] is the time of the newest message seen, so a phone clock that runs slow can't leave it "unread". */
    fun markRead(context: Context, uid: String, roomId: String, atLeast: Long = 0L) {
        val at = maxOf(System.currentTimeMillis(), atLeast)
        prefs(context).edit().putLong("chat_read_${uid}_$roomId", at).apply()
        versionState.intValue = versionState.intValue + 1
    }
}

/** The "read receipts" switch. When off, you don't tell others you've read their messages and you don't see theirs. */
object ReadReceipts {
    private const val KEY = "read_receipts"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}

/** The other person in a private conversation. */
fun ChatRoom.otherUid(me: String): String = memberIds.firstOrNull { it != me } ?: ""

fun ChatRoom.otherName(me: String): String = memberNames[otherUid(me)].orEmpty()

/** What to show as the conversation's name: the other person's name for a private message. */
fun ChatRoom.displayName(me: String): String = if (direct) otherName(me) else (if (channel) "\uD83D\uDCE2 " else "") + name

/** Two people always share one private conversation, whoever starts it. */
fun directRoomId(a: String, b: String): String = "dm_" + listOf(a, b).sorted().joinToString("_")
