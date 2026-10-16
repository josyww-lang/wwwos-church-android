@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package org.yourchurch.app.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.ChatMessage
import org.yourchurch.app.data.ChatReadStore
import org.yourchurch.app.data.ChatRoom
import org.yourchurch.app.data.ChatState
import org.yourchurch.app.data.ChatViewModel
import org.yourchurch.app.data.ReadReceipts
import org.yourchurch.app.data.ScheduledViewModel
import org.yourchurch.app.data.VoiceRecorder
import org.yourchurch.app.data.displayName
import org.yourchurch.app.data.otherName
import org.yourchurch.app.data.otherUid
import org.yourchurch.app.ui.common.EditedTag
import org.yourchurch.app.ui.common.GroupAvatar
import org.yourchurch.app.ui.common.PersonAvatar
import org.yourchurch.app.ui.common.SkeletonCards
import org.yourchurch.app.ui.theme.groupColor
import org.yourchurch.app.ui.theme.isAppDark

/** The quick reactions offered on a message. (The database rules allow exactly these.) */
internal val REACTIONS = listOf("👍", "🙏", "💙", "🙌", "😊", "😢")

internal fun formatClock(date: Date?): String =
    if (date == null) "" else DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(date)

/** Time for today's messages, otherwise the date. */
internal fun formatWhen(date: Date?): String = when {
    date == null -> ""
    DateUtils.isToday(date.time) -> formatClock(date)
    else -> DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(date)
}

@Composable
fun RoomScreen(
    room: ChatRoom,
    myUid: String,
    myName: String,
    isModerator: Boolean,
    isAdmin: Boolean,
    state: ChatState,
    vm: ChatViewModel,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onMessageSender: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    // Unsent text is kept for each conversation, so it is still there when you come back.
    var text by rememberSaveable(room.id) { mutableStateOf(vm.draftFor(room.id)) }
    var replyTo by remember(room.id) { mutableStateOf<ChatMessage?>(null) }
    /** The message whose text I am changing. */
    var editing by remember(room.id) { mutableStateOf<ChatMessage?>(null) }
    var showMembers by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showPinned by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    /** The person whose card is open: user id and name. */
    var card by remember { mutableStateOf<Pair<String, String>?>(null) }

    val listState = rememberLazyListState()

    // Polls, searching this chat, and messages to send later.
    var showPoll by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var showScheduledList by remember { mutableStateOf(false) }
    val scheduledVm: ScheduledViewModel = viewModel(key = "scheduled-$myUid")
    LaunchedEffect(myUid) { scheduledVm.start(myUid) }
    val scheduledAll by scheduledVm.items.collectAsStateWithLifecycle()
    val scheduledHere = scheduledAll.filter { it.roomId == room.id }
    val scheduledToast = stringResource(R.string.chat_scheduled_ok)
    // The group's own colour (private messages use the app's colour).
    val accent = groupColor(if (room.direct) 0 else room.color)
    val onAccent = if (room.direct || room.color == 0) MaterialTheme.colorScheme.onPrimary else Color.White

    // Photos: choosing one, a preview before it is sent, and opening a received one full screen.
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    var viewingPhoto by remember { mutableStateOf<ChatMessage?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pendingPhoto = uri
    }
    pendingPhoto?.let { uri ->
        PhotoPreviewDialog(
            uri = uri,
            onSend = { caption ->
                vm.sendPhoto(context, room, uri, caption, replyTo)
                replyTo = null
                pendingPhoto = null
            },
            onCancel = { pendingPhoto = null }
        )
    }
    viewingPhoto?.let { PhotoViewerDialog(message = it, onDismiss = { viewingPhoto = null }) }
    if (showPoll) {
        PollDialog(onSend = { question, options -> vm.sendPoll(room, question, options); showPoll = false }, onDismiss = { showPoll = false })
    }
    if (showSearch) {
        ChatSearchDialog(
            messages = state.messages,
            onPick = { id ->
                showSearch = false
                scope.launch {
                    val index = state.messages.asReversed().indexOfFirst { it.id == id }
                    if (index >= 0) listState.animateScrollToItem(index)
                }
            },
            onDismiss = { showSearch = false }
        )
    }
    if (showSchedule) {
        ScheduleDialog(
            onSchedule = { date ->
                showSchedule = false
                scope.launch {
                    scheduledVm.schedule(room.id, text.trim(), myName, date).onSuccess {
                        text = ""
                        Toast.makeText(context, scheduledToast, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showSchedule = false }
        )
    }
    if (showScheduledList) {
        ScheduledListDialog(items = scheduledHere, onCancel = { scheduledVm.cancel(it) }, onDismiss = { showScheduledList = false })
    }

    // Voice messages: tap the microphone, speak, then send it (or bin it).
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableIntStateOf(0) }
    val micDeniedToast = stringResource(R.string.chat_voice_denied)
    DisposableEffect(Unit) { onDispose { recorder.cancel(); VoicePlayback.stop() } }
    val finishRecording: (Boolean) -> Unit = { send ->
        recording = false
        val result = recorder.stop()
        if (send && result != null) {
            vm.sendVoice(room, result.first, result.second, replyTo)
            replyTo = null
        } else {
            result?.first?.delete()
        }
    }
    LaunchedEffect(recording) {
        if (recording) {
            while (recording) {
                elapsedMs = recorder.elapsedMs()
                if (elapsedMs >= VoiceRecorder.MAX_MS) {
                    finishRecording(true)
                    break
                }
                delay(200)
            }
        } else {
            elapsedMs = 0
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recording = recorder.start()
        else Toast.makeText(context, micDeniedToast, Toast.LENGTH_LONG).show()
    }
    val startRecording: () -> Unit = {
        VoicePlayback.stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recording = recorder.start()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    val reportedToast = stringResource(R.string.chat_report_sent)
    val sendFailedToast = stringResource(R.string.chat_send_failed)

    // Read receipts: tell the others when I have seen their newest message.
    val receiptsOn = remember { ReadReceipts.isEnabled(context) }
    val newestFromOthers = state.messages.lastOrNull { it.senderId != myUid && !it.pending }
    LaunchedEffect(newestFromOthers?.id, receiptsOn) {
        if (receiptsOn) vm.markRoomRead(room.id, newestFromOthers?.createdAt)
    }
    val latestMineId = state.messages.lastOrNull { it.senderId == myUid && !it.deleted }?.id
    var seenBy by remember { mutableStateOf<SeenInfo?>(null) }

    // Scroll to the newest message when one arrives, and remember this group was read.
    val newestId = state.messages.lastOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null) {
            listState.animateScrollToItem(0)
            ChatReadStore.markRead(context, myUid, room.id, state.messages.lastOrNull()?.createdAt?.time ?: 0L)
        }
    }
    DisposableEffect(room.id) {
        ChatReadStore.markRead(context, myUid, room.id)
        onDispose {
            ChatReadStore.markRead(context, myUid, room.id)
            vm.saveDraft(room.id, text)
        }
    }
    LaunchedEffect(state.sendFailed) {
        if (state.sendFailed) {
            Toast.makeText(context, sendFailedToast, Toast.LENGTH_SHORT).show()
            vm.clearSendFailed()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    // Tap the title to see who is in the group.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !room.direct) { showMembers = true }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (room.direct) {
                            PersonAvatar(uid = room.otherUid(myUid), name = room.otherName(myUid), size = 36.dp, viewable = true)
                        } else {
                            GroupAvatar(room.name, room.color, 36.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                room.displayName(myUid),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!room.direct) {
                                Text(
                                    stringResource(R.string.chat_members_count, room.memberIds.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    if (scheduledHere.isNotEmpty()) {
                        IconButton(onClick = { showScheduledList = true }) {
                            Icon(Icons.Outlined.Schedule, contentDescription = stringResource(R.string.chat_scheduled_list), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.chat_search), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (room.direct) {
                        // Mute or unmute this conversation.
                        val mutedHere = room.id in state.muted
                        IconButton(onClick = { vm.toggleMute(room.id) }) {
                            Icon(
                                if (mutedHere) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                                contentDescription = stringResource(if (mutedHere) R.string.chat_unmute else R.string.chat_mute),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // A private conversation has no group info, but you can block the other person.
                        val blocked = room.otherUid(myUid) in state.blocked
                        IconButton(onClick = { confirmBlock = true }) {
                            Icon(
                                Icons.Outlined.Block,
                                contentDescription = stringResource(if (blocked) R.string.dm_unblock else R.string.dm_block),
                                tint = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        if (isModerator) {
                            IconButton(onClick = { showAdd = true }) {
                                Icon(Icons.Outlined.PersonAdd, contentDescription = stringResource(R.string.chat_add_people))
                            }
                        }
                        IconButton(onClick = onInfo) {
                            Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.chat_info))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (room.channel && !isAdmin) {
                Surface(tonalElevation = 3.dp) {
                    Text(
                        stringResource(R.string.chat_channel_readonly),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                    )
                }
            } else if (room.direct && !state.dmEnabled) {
                Surface(tonalElevation = 3.dp) {
                    Text(
                        stringResource(R.string.dm_disabled_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                    )
                }
            } else Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                    // When replying, show which message is being answered.
                    if (state.sendingPhoto) LinearProgressIndicator(Modifier.fillMaxWidth())
                    editing?.let { EditBar(onCancel = { editing = null; text = "" }) }
                    replyTo?.let { target -> ReplyBar(message = target, onCancel = { replyTo = null }) }
                    if (recording) {
                        RecordingBar(
                            elapsedMs = elapsedMs,
                            accent = accent,
                            onCancel = { finishRecording(false) },
                            onSend = { finishRecording(true) }
                        )
                    } else Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (editing == null) {
                            IconButton(
                                onClick = {
                                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                enabled = !state.sendingPhoto
                            ) {
                                Icon(
                                    Icons.Outlined.AddPhotoAlternate,
                                    contentDescription = stringResource(R.string.chat_attach_photo),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showPoll = true }, enabled = !state.sendingPhoto) {
                                Icon(
                                    Icons.Outlined.BarChart,
                                    contentDescription = stringResource(R.string.chat_poll),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(2000) },
                            placeholder = { Text(stringResource(R.string.chat_message_hint)) },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        )
                        if (text.isNotBlank() && editing == null && !room.direct) {
                            // Send this one later.
                            IconButton(onClick = { showSchedule = true }) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    contentDescription = stringResource(R.string.chat_schedule),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (text.isBlank() && editing == null) {
                            IconButton(onClick = startRecording, enabled = !state.sendingPhoto) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = stringResource(R.string.chat_voice_record),
                                    tint = accent
                                )
                            }
                        } else IconButton(
                            onClick = {
                                val clean = text.trim()
                                val target = editing
                                if (clean.isNotEmpty()) {
                                    if (target != null) {
                                        // Only save if the text really changed.
                                        if (clean != target.text.trim()) vm.edit(room, target, clean)
                                        editing = null
                                    } else {
                                        vm.send(room, clean, replyTo)
                                        replyTo = null
                                    }
                                    text = ""
                                }
                            },
                            enabled = text.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.chat_send),
                                tint = if (text.isNotBlank()) accent
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // The pinned message, always visible at the top of a group.
            if (!room.direct && room.pinnedMessageId.isNotBlank()) {
                PinnedBanner(
                    name = room.pinnedName,
                    text = room.pinnedText,
                    canUnpin = isModerator,
                    onClick = { showPinned = true },
                    onUnpin = { vm.unpin(room) }
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.messagesLoading -> SkeletonCards(Modifier.align(Alignment.TopCenter))
                    state.messages.isEmpty() -> Text(
                        stringResource(R.string.chat_no_messages),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val newestFirst = state.messages.asReversed()
                        itemsIndexed(newestFirst, key = { _, m -> m.id }) { index, message ->
                            Column {
                                // A date label above the first message of each day.
                                val older = newestFirst.getOrNull(index + 1)
                                if (older == null || !sameDay(older.createdAt, message.createdAt)) {
                                    DateChip(message.createdAt)
                                }
                                val isMine = message.senderId == myUid
                                val sentAt = message.createdAt?.time ?: Long.MAX_VALUE
                                val seenUids = if (isMine && receiptsOn) {
                                    room.memberIds.filter { other ->
                                        other != myUid && (state.reads[other]?.time ?: 0L) >= sentAt
                                    }
                                } else emptyList()
                                val status = when {
                                    !isMine || message.deleted -> null
                                    message.pending -> MessageStatus.Sending
                                    seenUids.isNotEmpty() -> MessageStatus.Seen
                                    else -> MessageStatus.Sent
                                }
                                val isLatestMine = message.id == latestMineId
                                val seenLabel = when {
                                    !isLatestMine || seenUids.isEmpty() -> null
                                    room.direct -> stringResource(R.string.chat_seen)
                                    seenUids.size >= room.memberIds.size - 1 -> stringResource(R.string.chat_seen_by_all)
                                    else -> stringResource(R.string.chat_seen_by_count, seenUids.size)
                                }
                                MessageBubble(
                                    message = message,
                                    mine = isMine,
                                    isGroupOwner = room.createdBy.isNotEmpty() && message.senderId == room.createdBy,
                                    myUid = myUid,
                                    status = status,
                                    seenLabel = seenLabel,
                                    onSeenBy = if (isMine && !room.direct && receiptsOn && !message.deleted && !message.pending) {
                                        {
                                        seenBy = SeenInfo(
                                            seen = seenUids
                                                .mapNotNull { id -> state.reads[id]?.let { at -> room.memberNames[id].orEmpty() to at } }
                                                .sortedBy { it.second },
                                            notYet = room.memberIds
                                                .filter { it != myUid && it !in seenUids }
                                                .map { room.memberNames[it].orEmpty() }
                                        )
                                    }
                                    } else null,
                                    canDelete = isModerator || isMine,
                                    onDelete = { vm.removeMessage(room, message) },
                                    onOpenImage = if (message.imagePath.isNotBlank() && !message.deleted) {
                                        { viewingPhoto = message }
                                    } else null,
                                    accent = accent,
                                    onAccent = onAccent,
                                    onReport = {
                                        vm.report(room, message)
                                        Toast.makeText(context, reportedToast, Toast.LENGTH_SHORT).show()
                                    },
                                    onReply = { if (!message.pending) replyTo = message },
                                    onReact = { emoji -> if (!message.pending) vm.react(room, message, emoji) },
                                    onVote = { option -> if (!message.pending) vm.vote(room, message, option) },
                                    nameOf = { id -> room.memberNames[id].orEmpty() },
                                    onEdit = if (isMine && !message.deleted && !message.pending && message.kind == "text") {
                                        { editing = message; replyTo = null; text = message.text }
                                    } else null,
                                    pinned = room.pinnedMessageId == message.id,
                                    onPin = if (isModerator && !room.direct && !message.deleted && !message.pending) {
                                        { vm.togglePin(room, message) }
                                    } else null,
                                    onDirect = if (state.dmEnabled && !room.direct && !isMine) {
                                        { onMessageSender(message.senderId, message.senderName) }
                                    } else null,
                                    // Tap someone's picture or name to open their card.
                                    onSenderClick = if (!room.direct && !isMine) {
                                        { card = message.senderId to message.senderName }
                                    } else null
                                )
                            }
                        }
                        if (state.canLoadMore) {
                            item(key = "older") {
                                TextButton(onClick = { vm.loadOlder() }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.chat_load_older))
                                }
                            }
                        }
                    }
                }

                // A button to jump back down to the newest message after scrolling up.
                if (showJump) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.chat_jump_latest))
                    }
                }
            }
        }
    }

    if (confirmBlock) {
        val otherId = room.otherUid(myUid)
        val otherLabel = room.otherName(myUid)
        val isBlocked = otherId in state.blocked
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = {
                Text(stringResource(if (isBlocked) R.string.dm_unblock_title else R.string.dm_block_title, otherLabel))
            },
            text = { Text(stringResource(if (isBlocked) R.string.dm_unblock_text else R.string.dm_block_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    if (isBlocked) vm.unblock(otherId) else vm.block(otherId)
                }) { Text(stringResource(if (isBlocked) R.string.dm_unblock else R.string.dm_block)) }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showPinned) {
        AlertDialog(
            onDismissRequest = { showPinned = false },
            title = { Text(stringResource(R.string.chat_pinned_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (room.pinnedName.isNotBlank()) {
                        Text(
                            room.pinnedName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinkifiedText(room.pinnedText, MaterialTheme.colorScheme.onSurface)
                }
            },
            confirmButton = { TextButton(onClick = { showPinned = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (showMembers) {
        MembersSheet(
            room = room,
            myUid = myUid,
            isModerator = isModerator,
            state = state,
            dmEnabled = state.dmEnabled,
            vm = vm,
            onMessage = { id, name -> showMembers = false; onMessageSender(id, name) },
            onOpenCard = { id, name -> showMembers = false; card = id to name },
            onAdd = { showMembers = false; showAdd = true },
            onDismiss = { showMembers = false }
        )
    }

    card?.let { (cardId, cardName) ->
        PersonCardSheet(
            uid = cardId,
            name = cardName,
            subtitle = if (cardId == room.createdBy) R.string.chat_owner else null,
            phone = state.roomPhones[cardId].orEmpty(),
            isMe = cardId == myUid,
            showMessage = !room.direct,
            dmEnabled = state.dmEnabled,
            onMessage = { card = null; onMessageSender(cardId, cardName) },
            onDismiss = { card = null }
        )
    }

    seenBy?.let { info ->
        AlertDialog(
            onDismissRequest = { seenBy = null },
            title = { Text(stringResource(R.string.chat_seen_by)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (info.seen.isEmpty()) {
                        Text(stringResource(R.string.chat_seen_by_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    info.seen.forEach { (name, at) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(name.ifBlank { "?" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(formatSeen(at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (info.notYet.isNotEmpty()) {
                        Text(
                            stringResource(R.string.chat_not_seen_yet),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        info.notYet.forEach {
                            Text(it.ifBlank { "?" }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { seenBy = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (showAdd) {
        AddMembersDialog(
            room = room,
            myUid = myUid,
            isAdmin = isAdmin,
            state = state,
            vm = vm,
            onDismiss = { showAdd = false }
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    myUid: String,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    pinned: Boolean,
    onPin: (() -> Unit)?,
    onDirect: (() -> Unit)? = null,
    onSenderClick: (() -> Unit)? = null,
    status: MessageStatus? = null,
    seenLabel: String? = null,
    onSeenBy: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onVote: ((Int) -> Unit)? = null,
    onOpenImage: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    onAccent: Color = MaterialTheme.colorScheme.onPrimary,
    isGroupOwner: Boolean = false,
    nameOf: (String) -> String = { it }
) {
    val context = LocalContext.current
    val copiedText = stringResource(R.string.chat_copied)
    var menuOpen by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showChangeReaction by remember { mutableStateOf(false) }
    var showWho by remember { mutableStateOf(false) }
    val bubbleColor = if (mine) accent else MaterialTheme.colorScheme.surface
    val textColor = if (mine) onAccent else MaterialTheme.colorScheme.onSurface
    // Seen ticks stand out in green; unseen ticks are faded.
    val seenTint = if (isAppDark()) Color(0xFF065F46) else Color(0xFF86EFAC)

    // Swipe a message to the right to reply to it (as often as you like).
    var drag by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val replyDistance = with(LocalDensity.current) { 64.dp.toPx() }
    val currentReply by rememberUpdatedState(onReply)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(drag.roundToInt(), 0) }
            .pointerInput(message.id) {
                var passed = false
                detectHorizontalDragGestures(
                    onDragStart = { passed = false },
                    onDragEnd = {
                        if (drag >= replyDistance && !message.deleted) currentReply()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f }
                ) { _, amount ->
                    drag = (drag + amount).coerceIn(0f, replyDistance * 1.3f)
                    if (!passed && drag >= replyDistance) {
                        passed = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            },
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            PersonAvatar(
                uid = message.senderId,
                name = message.senderName,
                size = 32.dp,
                modifier = if (onSenderClick != null) Modifier.clickable { onSenderClick() } else Modifier
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Box {
                Column(
                    modifier = Modifier
                        .widthIn(max = if (mine) 300.dp else 260.dp)
                        .shadow(2.dp, RoundedCornerShape(18.dp))
                    .background(bubbleColor, RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = {},
                            onDoubleClick = { if (!message.deleted && !message.pending) onReact(REACTIONS.first()) },
                            onLongClick = { if (!message.deleted) menuOpen = true }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (!mine) {
                        // The name, and a small title for the church's admins and leaders (and the group's owner).
                        val title = when {
                            message.role == "admin" -> R.string.chat_role_admin
                            message.role == "leader" -> R.string.chat_role_leader
                            isGroupOwner -> R.string.chat_role_group_admin
                            else -> null
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                message.senderName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = if (onSenderClick != null) Modifier.clickable { onSenderClick() } else Modifier
                            )
                            if (title != null) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(title),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    // If this message is a reply, show the message it answers.
                    if (message.replyToId.isNotBlank() && !message.deleted) {
                        Column(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .background(textColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                message.replyToName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                            Text(
                                message.replyToText.ifBlank { "…" },
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (message.deleted) {
                        Text(
                            stringResource(R.string.chat_message_removed),
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    } else {
                        if (message.imagePath.isNotBlank()) {
                            PhotoThumb(message = message, onClick = onOpenImage)
                        }
                        if (message.audioPath.isNotBlank()) {
                            VoiceBubble(message = message, color = textColor)
                        }
                        if (message.text.isNotBlank()) {
                            LinkifiedText(message.text, textColor)
                        }
                        if (message.pollOptions.isNotEmpty()) {
                            PollView(message = message, myUid = myUid, color = textColor, onVote = { onVote?.invoke(it) })
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatClock(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        if (message.edited && !message.deleted) {
                            Spacer(Modifier.width(6.dp))
                            EditedTag(
                                current = message.text,
                                history = message.history,
                                color = textColor.copy(alpha = 0.85f)
                            )
                        }
                        if (status != null) {
                            Spacer(Modifier.width(4.dp))
                            // A clock: still sending. One tick: sent, not seen yet. Two green ticks: seen.
                            Icon(
                                imageVector = when (status) {
                                    MessageStatus.Sending -> Icons.Outlined.Schedule
                                    MessageStatus.Sent -> Icons.Filled.Done
                                    MessageStatus.Seen -> Icons.Filled.DoneAll
                                },
                                contentDescription = null,
                                tint = if (status == MessageStatus.Seen) seenTint else textColor.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (seenLabel != null) {
                        Text(
                            seenLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.85f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Quick reactions
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        REACTIONS.forEach { emoji ->
                            Text(
                                emoji,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (message.reactions[myUid] == emoji) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable { menuOpen = false; onReact(emoji) }
                                    .padding(8.dp)
                            )
                        }
                        // Any other emoji
                        Text(
                            "➕",
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { menuOpen = false; showPicker = true }
                                .padding(8.dp)
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_reply)) },
                        onClick = { menuOpen = false; onReply() }
                    )
                    if (onEdit != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_edit)) },
                            onClick = { menuOpen = false; onEdit() }
                        )
                    }
                    if (!message.deleted && message.text.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_copy)) },
                            onClick = {
                                menuOpen = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("message", message.text))
                                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    if (onSeenBy != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_seen_by)) },
                            onClick = { menuOpen = false; onSeenBy() }
                        )
                    }
                    if (onPin != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (pinned) R.string.chat_unpin else R.string.chat_pin)) },
                            onClick = { menuOpen = false; onPin() }
                        )
                    }
                    if (onDirect != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dm_menu_item)) },
                            onClick = { menuOpen = false; onDirect() }
                        )
                    }
                    if (!mine) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_report)) },
                            onClick = { menuOpen = false; onReport() }
                        )
                    }
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (mine) R.string.chat_unsend else R.string.chat_delete_message), color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }

            // Reactions under the message. Tap one to choose it (or take yours back). Long-press to see who.
            if (message.reactions.isNotEmpty() && !message.deleted) {
                val counts = message.reactions.values.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    counts.forEach { (emoji, count) ->
                        val iReacted = message.reactions[myUid] == emoji
                        Text(
                            "$emoji $count",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (iReacted) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .combinedClickable(
                                    onClick = { if (iReacted) showChangeReaction = true else onReact(emoji) },
                                    onLongClick = { showWho = true }
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            if (showChangeReaction) {
                AlertDialog(
                    onDismissRequest = { showChangeReaction = false },
                    title = { Text(stringResource(R.string.chat_reaction_change)) },
                    text = {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            REACTIONS.forEach { emoji ->
                                Text(
                                    emoji,
                                    fontSize = 28.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (message.reactions[myUid] == emoji) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            showChangeReaction = false
                                            if (message.reactions[myUid] != emoji) onReact(emoji)
                                        }
                                        .padding(10.dp)
                                )
                            }
                            Text(
                                "➕",
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { showChangeReaction = false; showPicker = true }
                                    .padding(10.dp)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showChangeReaction = false
                            message.reactions[myUid]?.let { onReact(it) }   // choosing the same one again takes it back
                        }) { Text(stringResource(R.string.chat_reaction_remove)) }
                    },
                    dismissButton = { TextButton(onClick = { showChangeReaction = false }) { Text(stringResource(R.string.cancel)) } }
                )
            }
            if (showPicker) {
                EmojiPickerDialog(onPick = { showPicker = false; onReact(it) }, onDismiss = { showPicker = false })
            }
            if (showWho) {
                AlertDialog(
                    onDismissRequest = { showWho = false },
                    title = { Text(stringResource(R.string.chat_reactions_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            message.reactions.entries.sortedBy { nameOf(it.key).lowercase() }.forEach { (reactor, emoji) ->
                                Text("$emoji  ${nameOf(reactor).ifBlank { "?" }}", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showWho = false }) { Text(stringResource(R.string.close)) } }
                )
            }
        }
    }
}

/** The bar above the message box while replying to someone. */
@Composable
private fun ReplyBar(message: ChatMessage, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(3.dp).height(36.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.chat_replying_to, message.senderName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                message.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_reply_cancel))
        }
    }
}

/** The pinned message bar at the top of a group. */
@Composable
private fun PinnedBanner(name: String, text: String, canUnpin: Boolean, onClick: () -> Unit, onUnpin: () -> Unit) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.chat_pinned_title) + if (name.isNotBlank()) "  ·  $name" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (canUnpin) {
                IconButton(onClick = onUnpin) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_unpin),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** A small round badge with the group's first letter. */

/** "Today", "Yesterday" or the date, shown between messages from different days. */
@Composable
private fun DateChip(date: Date?) {
    if (date == null) return
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
    val label = when {
        sameDay(date, Date()) -> stringResource(R.string.chat_today)
        sameDay(date, yesterday) -> stringResource(R.string.chat_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(date)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/** True if both times are on the same calendar day. A missing time (message still sending) counts as the same day. */
private fun sameDay(a: Date?, b: Date?): Boolean {
    if (a == null || b == null) return true
    val first = Calendar.getInstance().apply { time = a }
    val second = Calendar.getInstance().apply { time = b }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

/** How far a message you sent has got. */
internal enum class MessageStatus { Sending, Sent, Seen }


/** The bar above the message box while changing a message. */
@Composable
private fun EditBar(onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(3.dp).height(24.dp)
                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.chat_editing),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
        }
    }
}


/** Who has seen a message, with the time each of them last opened the conversation after it, and who has not. */
private class SeenInfo(val seen: List<Pair<String, Date>>, val notYet: List<String>)

/** "14:32" for today, otherwise the date and the time. */
internal fun formatSeen(date: Date): String =
    if (DateUtils.isToday(date.time)) formatClock(date)
    else DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(date) + " " + formatClock(date)
