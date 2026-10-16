package org.yourchurch.app.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatState(
    /** null while we are still checking */
    val approved: Boolean? = null,
    val myRooms: List<ChatRoom> = emptyList(),
    val directory: List<DirectoryMember> = emptyList(),
    /** True once the list of approved members has arrived (or failed). */
    val directoryLoaded: Boolean = false,
    val directoryFailed: Boolean = false,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    // the group that is open right now
    val messages: List<ChatMessage> = emptyList(), // oldest first
    val messagesLoading: Boolean = false,
    val canLoadMore: Boolean = false,
    val sendFailed: Boolean = false,
    /** True while a photo is being uploaded. */
    val sendingPhoto: Boolean = false,
    /** Conversations I have muted (no notifications). */
    val muted: Set<String> = emptySet(),
    // admin only
    val people: List<PersonRow> = emptyList(),
    val reports: List<ChatReport> = emptyList(),
    // phone numbers shared with the open group
    /** uid -> number. Only filled in for the group's owner and admins. */
    val roomPhones: Map<String, String> = emptyMap(),
    /** Who I share my number with in the open group: "" (nobody), "leaders" or "group". */
    val myShare: String = "",
    /** When each person last opened the open conversation (for read receipts). */
    val reads: Map<String, java.util.Date> = emptyMap(),
    // private messages
    /** People I have blocked from sending me private messages. */
    val blocked: Set<String> = emptySet(),
    /** False when an admin has switched off private messages. */
    val dmEnabled: Boolean = true
)

class ChatViewModel : ViewModel() {
    private val repo = ChatRepository()

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var uid = ""
    private var myName = ""
    private var admin = false

    private var approvalJob: Job? = null
    private var dataJobs: List<Job> = emptyList()
    private var messagesJob: Job? = null
    private var currentRoomId: String? = null
    private var messageLimit = 50L

    private var users: List<UserRow> = emptyList()

    /** Call whenever the chat screen is shown. Safe to call repeatedly. */
    fun start(uid: String, name: String, isAdmin: Boolean, isOwner: Boolean = false) {
        myName = name
        admin = isAdmin
        repo.senderRole = if (isOwner) "admin" else if (isAdmin) "leader" else ""
        if (this.uid == uid) {
            updateAccess()
            return
        }
        this.uid = uid
        adminListed = false
        stopAll()
        _state.value = ChatState()
        approvalJob = viewModelScope.launch {
            repo.observeApproved(uid).collect { result ->
                result.onSuccess { ok ->
                    _state.update { it.copy(approved = ok) }
                    updateAccess()
                }.onFailure {
                    _state.update { it.copy(approved = false, loading = false) }
                }
            }
        }
        updateAccess()
    }

    private fun stopAll() {
        approvalJob?.cancel()
        dataJobs.forEach { it.cancel() }
        dataJobs = emptyList()
        messagesJob?.cancel()
        currentRoomId = null
    }

    private fun updateAccess() {
        val canUse = admin || _state.value.approved == true
        if (canUse && dataJobs.isEmpty()) {
            startData()
        } else if (!canUse && dataJobs.isNotEmpty()) {
            dataJobs.forEach { it.cancel() }
            dataJobs = emptyList()
        }
    }

    private fun startData() {
        val jobs = mutableListOf<Job>()
        jobs += viewModelScope.launch {
            repo.observeMyRooms(uid).collect { result ->
                result.onSuccess { list ->
                    _state.update {
                        it.copy(
                            myRooms = list.sortedByDescending { room -> room.lastActivity?.time ?: 0L },
                            loading = false,
                            loadFailed = false
                        )
                    }
                }.onFailure {
                    _state.update { it.copy(loading = false, loadFailed = true) }
                }
            }
        }
        jobs += viewModelScope.launch {
            repo.observeDirectory().collect { result ->
                result.onSuccess { list ->
                    _state.update {
                        it.copy(
                            directory = list.sortedBy { m -> m.name.lowercase() },
                            directoryLoaded = true,
                            directoryFailed = false
                        )
                    }
                    refreshPeople()
                    ensureAdminListed(list)
                }.onFailure {
                    _state.update { it.copy(directoryLoaded = true, directoryFailed = true) }
                }
            }
        }
        jobs += viewModelScope.launch {
            repo.observeBlocked(uid).collect { result ->
                result.onSuccess { set -> _state.update { it.copy(blocked = set) } }
            }
        }
        jobs += viewModelScope.launch {
            repo.observeDmEnabled().collect { result ->
                result.onSuccess { enabled -> _state.update { it.copy(dmEnabled = enabled) } }
            }
        }
        jobs += viewModelScope.launch {
            repo.observeMuted(uid).collect { result ->
                result.onSuccess { set -> _state.update { it.copy(muted = set) } }
            }
        }
        if (admin) {
            jobs += viewModelScope.launch {
                repo.observeUsers().collect { result ->
                    result.onSuccess { list ->
                        users = list
                        refreshPeople()
                    }
                }
            }
            jobs += viewModelScope.launch {
                repo.observeReports().collect { result ->
                    result.onSuccess { list -> _state.update { it.copy(reports = list) } }
                }
            }
        }
        dataJobs = jobs
    }

    private var adminListed = false

    /** Admins are always in the members list, so other people can find and add them. */
    private fun ensureAdminListed(list: List<DirectoryMember>) {
        if (!admin || adminListed || list.any { it.uid == uid }) return
        adminListed = true
        viewModelScope.launch { runCatching { repo.approve(uid, myName.ifBlank { "Admin" }, uid) } }
    }

    private fun refreshPeople() {
        val approvedIds = _state.value.directory.map { it.uid }.toSet()
        val rows = users
            .map { PersonRow(it.uid, it.name, it.email, it.phone, it.uid in approvedIds) }
            .sortedWith(compareBy<PersonRow> { it.approved }.thenBy { it.name.lowercase() })
        _state.update { it.copy(people = rows) }
    }

    // ---------- The open group ----------

    /** [moderator] is true for the group's owner and for admins, who can see shared phone numbers. */
    fun openRoom(roomId: String, moderator: Boolean, trackReads: Boolean) {
        if (currentRoomId != roomId) {
            currentRoomId = roomId
            messageLimit = 50
            _state.update { it.copy(messages = emptyList(), messagesLoading = true, canLoadMore = false) }
            listenMessages()
        }
        listenPhones(roomId, moderator)
        listenReads(roomId, trackReads)
    }

    fun closeRoom() {
        messagesJob?.cancel()
        readsJob?.cancel()
        readsKey = null
        trackingReads = false
        phonesJobs.forEach { it.cancel() }
        phonesJobs = emptyList()
        phonesKey = null
        currentRoomId = null
        _state.update {
            it.copy(
                messages = emptyList(), messagesLoading = false, canLoadMore = false,
                roomPhones = emptyMap(), myShare = "", reads = emptyMap()
            )
        }
    }

    private var readsJob: Job? = null
    private var readsKey: String? = null
    private var trackingReads = false
    private val lastMarked = HashMap<String, Long>()

    /** Watches when other people last opened this conversation (small groups and private messages only). */
    private fun listenReads(roomId: String, track: Boolean) {
        val key = "$roomId|$track"
        if (readsKey == key) return
        readsKey = key
        trackingReads = track
        readsJob?.cancel()
        if (!track) {
            _state.update { it.copy(reads = emptyMap()) }
            return
        }
        readsJob = viewModelScope.launch {
            repo.observeReads(roomId).collect { result ->
                result.onSuccess { map -> _state.update { it.copy(reads = map) } }
            }
        }
    }

    /**
     * Tells the others I have seen messages up to [newestFromOthers]. Called when someone else's newer
     * message is on my screen. Only writes when there is something new.
     */
    fun markRoomRead(roomId: String, newestFromOthers: java.util.Date?) {
        if (!trackingReads) return
        val t = newestFromOthers?.time ?: return
        if (t <= (lastMarked[roomId] ?: 0L)) return
        lastMarked[roomId] = t
        viewModelScope.launch { runCatching { repo.markReadRemote(roomId, uid) } }
    }

    private var phonesJobs: List<Job> = emptyList()
    private var phonesKey: String? = null

    private fun listenPhones(roomId: String, moderator: Boolean) {
        val key = "$roomId|$moderator"
        if (phonesKey == key) return
        phonesKey = key
        phonesJobs.forEach { it.cancel() }
        val jobs = mutableListOf<Job>()
        jobs += viewModelScope.launch {
            repo.observeMyShare(roomId, uid).collect { result ->
                result.onSuccess { audience -> _state.update { it.copy(myShare = audience) } }
            }
        }
        jobs += viewModelScope.launch {
            repo.observeRoomPhones(roomId, moderator).collect { result ->
                result.onSuccess { phones -> _state.update { it.copy(roomPhones = phones) } }
            }
        }
        phonesJobs = jobs
    }

    /** Choose who can see my number in this group: "" (nobody), "leaders" or "group". */
    fun setShare(room: ChatRoom, audience: String, myPhone: String) {
        viewModelScope.launch {
            runCatching {
                if (audience.isEmpty()) repo.stopSharingPhone(room.id, uid)
                else repo.sharePhone(room.id, uid, myPhone, audience)
            }
        }
    }

    fun loadOlder() {
        messageLimit += 50
        listenMessages()
    }

    private fun listenMessages() {
        messagesJob?.cancel()
        val id = currentRoomId ?: return
        val limit = messageLimit
        messagesJob = viewModelScope.launch {
            repo.observeMessages(id, limit).collect { result ->
                result.onSuccess { list ->
                    _state.update {
                        it.copy(
                            messages = list.reversed(),
                            messagesLoading = false,
                            canLoadMore = list.size >= limit
                        )
                    }
                }.onFailure {
                    _state.update { it.copy(messagesLoading = false) }
                }
            }
        }
    }

    // ---------- Actions ----------

    suspend fun createRoom(
        name: String,
        description: String,
        open: Boolean,
        invited: Map<String, String>,
        channel: Boolean = false
    ): Result<String> = runCatching { repo.createRoom(name, description, open, uid, myName, invited, channel) }

    fun leave(room: ChatRoom) {
        viewModelScope.launch { runCatching { repo.removeMember(room.id, uid) } }
    }

    /**
     * Opens the private conversation with one person, creating it the first time.
     * [onReady] gets the conversation's id; [onFail] is called if it couldn't be started.
     */
    fun startDirect(otherUid: String, otherName: String, onReady: (String) -> Unit, onFail: () -> Unit) {
        if (otherUid.isEmpty() || otherUid == uid) return
        val id = directRoomId(uid, otherUid)
        if (_state.value.myRooms.any { it.id == id }) {
            onReady(id)
            return
        }
        viewModelScope.launch {
            runCatching { repo.createDirect(id, uid, myName, otherUid, otherName) }
                .onSuccess { onReady(id) }
                .onFailure { onFail() }
        }
    }

    fun block(otherUid: String) {
        viewModelScope.launch { runCatching { repo.block(uid, otherUid) } }
    }

    fun unblock(otherUid: String) {
        viewModelScope.launch { runCatching { repo.unblock(uid, otherUid) } }
    }

    /** Admin only: allow or stop all private messaging. */
    fun setDmEnabled(enabled: Boolean) {
        viewModelScope.launch { runCatching { repo.setDmEnabled(enabled) } }
    }

    fun removeMember(room: ChatRoom, memberUid: String) {
        viewModelScope.launch { runCatching { repo.removeMember(room.id, memberUid) } }
    }

    fun addMembers(room: ChatRoom, people: Map<String, String>) {
        viewModelScope.launch { runCatching { repo.addMembers(room.id, people) } }
    }

    fun deleteRoom(room: ChatRoom) {
        viewModelScope.launch { runCatching { repo.deleteRoom(room.id) } }
    }

    fun sendPoll(room: ChatRoom, question: String, options: List<String>) {
        repo.sendPoll(room, question, options, uid, myName) { _state.update { it.copy(sendFailed = true) } }
    }

    /** Votes for an option; tapping the option already chosen takes the vote back. */
    fun vote(room: ChatRoom, message: ChatMessage, option: Int) {
        viewModelScope.launch {
            runCatching { repo.vote(room.id, message.id, uid, if (message.votes[uid] == option) null else option) }
        }
    }

    fun send(room: ChatRoom, text: String, reply: ChatMessage? = null) {
        repo.sendMessage(room, text, uid, myName, reply) {
            _state.update { it.copy(sendFailed = true) }
        }
    }

    // Unsent text, kept for each conversation so it isn't lost when you go back.
    private val drafts = HashMap<String, String>()

    fun draftFor(roomId: String): String = drafts[roomId].orEmpty()

    fun saveDraft(roomId: String, text: String) {
        if (text.isBlank()) drafts.remove(roomId) else drafts[roomId] = text
    }

    /** Choose a reaction. Choosing another one replaces mine; choosing the same one takes it back. */
    fun react(room: ChatRoom, message: ChatMessage, emoji: String) {
        val chosen = if (message.reactions[uid] == emoji) null else emoji
        viewModelScope.launch { runCatching { repo.react(room.id, message.id, uid, chosen) } }
    }

    /** Change the text of one of my messages. */
    fun edit(room: ChatRoom, message: ChatMessage, newText: String) {
        viewModelScope.launch { runCatching { repo.editMessage(room.id, message, newText) } }
    }

    /** Owners and admins: pin a message to the top of the group, or unpin it if it is already pinned. */
    fun togglePin(room: ChatRoom, message: ChatMessage) {
        viewModelScope.launch {
            runCatching {
                if (room.pinnedMessageId == message.id) repo.unpin(room.id) else repo.pin(room.id, message)
            }
        }
    }

    fun unpin(room: ChatRoom) {
        viewModelScope.launch { runCatching { repo.unpin(room.id) } }
    }

    fun clearSendFailed() = _state.update { it.copy(sendFailed = false) }

    fun removeMessage(room: ChatRoom, message: ChatMessage) {
        viewModelScope.launch { runCatching { repo.removeMessage(room.id, message) } }
    }

    /** Shrinks and sends a picture picked from the gallery, with an optional caption. */
    fun sendPhoto(context: Context, room: ChatRoom, uri: Uri, caption: String, reply: ChatMessage?) {
        viewModelScope.launch {
            _state.update { it.copy(sendingPhoto = true) }
            val ok = runCatching {
                val prepared = ChatImages.prepare(context.applicationContext, uri) ?: error("unreadable picture")
                repo.sendPhoto(room, uid, myName, caption.trim().take(500), reply, prepared)
            }.isSuccess
            _state.update { it.copy(sendingPhoto = false, sendFailed = it.sendFailed || !ok) }
        }
    }

    fun setGroupColor(room: ChatRoom, index: Int) {
        viewModelScope.launch { runCatching { repo.setGroupColor(room.id, index) } }
    }

    /** Uploads and sends a recorded voice message. */
    fun sendVoice(room: ChatRoom, file: java.io.File, durationMs: Int, reply: ChatMessage?) {
        viewModelScope.launch {
            _state.update { it.copy(sendingPhoto = true) }
            val ok = runCatching { repo.sendVoice(room, uid, myName, file, durationMs, reply) }.isSuccess
            runCatching { file.delete() }
            _state.update { it.copy(sendingPhoto = false, sendFailed = it.sendFailed || !ok) }
        }
    }

    /** Mute or unmute a conversation: the notification server skips muted ones. */
    fun toggleMute(roomId: String) {
        val nowMuted = roomId !in _state.value.muted
        _state.update { it.copy(muted = if (nowMuted) it.muted + roomId else it.muted - roomId) }
        viewModelScope.launch { runCatching { repo.setMuted(uid, roomId, nowMuted) } }
    }

    fun report(room: ChatRoom, message: ChatMessage) {
        viewModelScope.launch { runCatching { repo.report(room, message, uid, myName) } }
    }

    // Admin
    fun approve(person: PersonRow) {
        val displayName = person.name.ifBlank { person.email.substringBefore('@') }
        viewModelScope.launch { runCatching { repo.approve(person.uid, displayName, uid) } }
    }

    fun revoke(person: PersonRow) {
        viewModelScope.launch { runCatching { repo.revoke(person.uid) } }
    }

    fun dismissReport(report: ChatReport) {
        viewModelScope.launch { runCatching { repo.dismissReport(report.id) } }
    }

    fun removeReportedMessage(report: ChatReport) {
        viewModelScope.launch {
            runCatching { repo.removeMessage(report.roomId, report.messageId) }
            runCatching { repo.dismissReport(report.id) }
        }
    }
}
