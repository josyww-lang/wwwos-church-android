@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package org.yourchurch.app.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yourchurch.app.ChatUiState
import org.yourchurch.app.PendingRoom
import org.yourchurch.app.R
import org.yourchurch.app.data.ChatReadStore
import org.yourchurch.app.data.ChatRoom
import org.yourchurch.app.data.ChatViewModel
import org.yourchurch.app.data.DirectoryMember
import org.yourchurch.app.data.PresenceInfo
import org.yourchurch.app.data.PresenceStore
import org.yourchurch.app.data.displayName
import org.yourchurch.app.data.otherName
import org.yourchurch.app.data.otherUid
import org.yourchurch.app.ui.common.AppHeader
import org.yourchurch.app.ui.common.GroupAvatar
import org.yourchurch.app.ui.common.PersonAvatar
import org.yourchurch.app.ui.common.PresenceLine
import org.yourchurch.app.ui.common.SkeletonCards
import org.yourchurch.app.ui.community.SectionTabs

@Composable
fun ChatScreen(
    uid: String,
    myName: String,
    myPhone: String,
    isAdmin: Boolean,
    isOwner: Boolean = false,
    accountMenu: @Composable () -> Unit,
    /** Tells the app to hide the bottom menu while a full-screen chat page is open. */
    onFullScreen: (Boolean) -> Unit,
    // One view model per signed-in person.
    vm: ChatViewModel = viewModel(key = "chat-$uid")
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(uid, myName, isAdmin, isOwner) { vm.start(uid, myName, isAdmin, isOwner) }

    var route by rememberSaveable { mutableStateOf("list") } // list, create, admin, room, info
    var roomId by rememberSaveable { mutableStateOf<String?>(null) }
    var readTick by remember { mutableIntStateOf(0) }
    var showNewMessage by remember { mutableStateOf(false) }
    var listTab by rememberSaveable { mutableIntStateOf(0) }
    var cardPerson by remember { mutableStateOf<DirectoryMember?>(null) }
    var pendingDelete by remember { mutableStateOf<ChatRoom?>(null) }
    var pendingLeave by remember { mutableStateOf<ChatRoom?>(null) }

    LaunchedEffect(route) {
        onFullScreen(route != "list")
        if (route == "list") readTick++
    }
    DisposableEffect(Unit) { onDispose { onFullScreen(false) } }

    val room = state.myRooms.firstOrNull { it.id == roomId }

    // Opening a conversation from a notification.
    val pendingRoom = PendingRoom.roomId
    LaunchedEffect(pendingRoom, state.myRooms, state.loading) {
        val target = pendingRoom ?: return@LaunchedEffect
        if (state.myRooms.any { it.id == target }) {
            roomId = target
            route = "room"
            PendingRoom.roomId = null
        } else if (!state.loading) {
            PendingRoom.roomId = null
        }
    }
    // Remember which conversation is on screen, so we don't send a notification about it.
    DisposableEffect(route, roomId) {
        ChatUiState.openRoomId = if (route == "room" || route == "info") roomId else null
        onDispose { ChatUiState.openRoomId = null }
    }

    // If the group disappears while we're looking at it (deleted, or we left), go back to the list.
    var roomSeen by remember(roomId) { mutableStateOf(false) }
    LaunchedEffect(room) {
        if (room != null) {
            roomSeen = true
        } else if (roomSeen && (route == "room" || route == "info")) {
            route = "list"
            roomId = null
        }
    }

    // Load messages (and shared phone numbers) while a group or its info page is open.
    val moderator = isAdmin || (room != null && !room.direct && room.createdBy == uid)
    val trackReads = room != null && (room.direct || room.memberIds.size <= 50)
    LaunchedEffect(route, roomId, moderator, trackReads) {
        val id = roomId
        if ((route == "room" || route == "info") && id != null) vm.openRoom(id, moderator, trackReads) else vm.closeRoom()
    }

    val canUse = isAdmin || state.approved == true

    // Start (or reopen) a private conversation with one person.
    val appContext = LocalContext.current
    val dmFailedText = stringResource(R.string.dm_start_failed)
    val dmOffText = stringResource(R.string.dm_disabled_note)
    val startDirect: (String, String) -> Unit = { otherUid, otherName ->
        vm.startDirect(
            otherUid = otherUid,
            otherName = otherName,
            onReady = { id -> roomId = id; route = "room" },
            onFail = { Toast.makeText(appContext, dmFailedText, Toast.LENGTH_SHORT).show() }
        )
    }

    when {
        route == "admin" && isAdmin -> AdminPeopleScreen(
            state = state,
            vm = vm,
            myUid = uid,
            onMessage = startDirect,
            onBack = { route = "list" }
        )

        route == "create" && canUse -> CreateGroupScreen(
            uid = uid,
            isAdmin = isAdmin,
            state = state,
            vm = vm,
            onBack = { route = "list" },
            onCreated = { id -> roomId = id; route = "room" }
        )

        route == "room" && room != null -> RoomScreen(
            room = room,
            myUid = uid,
            myName = myName,
            isModerator = isAdmin || (!room.direct && room.createdBy == uid),
            isAdmin = isAdmin,
            state = state,
            vm = vm,
            onBack = { route = "list"; roomId = null },
            onInfo = { route = "info" },
            onMessageSender = startDirect
        )

        route == "info" && room != null -> GroupInfoScreen(
            room = room,
            myUid = uid,
            myPhone = myPhone,
            isAdmin = isAdmin,
            isModerator = isAdmin || (!room.direct && room.createdBy == uid),
            state = state,
            vm = vm,
            dmEnabled = state.dmEnabled,
            onMessageMember = startDirect,
            onBack = { route = "room" },
            onLeftOrDeleted = { route = "list"; roomId = null }
        )

        else -> Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (canUse) {
                    var chooseNew by remember { mutableStateOf(false) }
                    Box {
                        ExtendedFloatingActionButton(
                            onClick = { chooseNew = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(18.dp),
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.chat_new_chat), fontWeight = FontWeight.Medium) }
                        )
                        DropdownMenu(expanded = chooseNew, onDismissRequest = { chooseNew = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_new_group)) },
                                onClick = { chooseNew = false; route = "create" }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_new_message)) },
                                onClick = {
                                    chooseNew = false
                                    if (state.dmEnabled) showNewMessage = true
                                    else Toast.makeText(appContext, dmOffText, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ChatHeader(accountMenu = accountMenu)
                if (isAdmin && canUse && !state.dmEnabled) {
                    DmOffBanner(onTurnOn = { vm.setDmEnabled(true) })
                }
                pendingDelete?.let { target ->
                    AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text(stringResource(R.string.chat_delete_group_title)) },
                        text = { Text("${target.name}\n\n${stringResource(R.string.chat_delete_group_text)}") },
                        confirmButton = {
                            TextButton(onClick = { vm.deleteRoom(target); pendingDelete = null }) {
                                Text(stringResource(R.string.chat_delete_group), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } }
                    )
                }
                pendingLeave?.let { target ->
                    AlertDialog(
                        onDismissRequest = { pendingLeave = null },
                        title = { Text(stringResource(R.string.chat_leave_title)) },
                        text = { Text(target.name) },
                        confirmButton = {
                            TextButton(onClick = { vm.leave(target); pendingLeave = null }) {
                                Text(stringResource(R.string.chat_leave), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = { TextButton(onClick = { pendingLeave = null }) { Text(stringResource(R.string.cancel)) } }
                    )
                }
                if (showNewMessage) {
                    NewMessageDialog(
                        directory = state.directory.filter { it.uid != uid },
                        loaded = state.directoryLoaded,
                        failed = state.directoryFailed,
                        onPick = { id, name -> showNewMessage = false; startDirect(id, name) },
                        onDismiss = { showNewMessage = false }
                    )
                }
                cardPerson?.let { member ->
                    PersonCardSheet(
                        uid = member.uid,
                        name = member.name,
                        subtitle = null,
                        phone = "",
                        isMe = false,
                        showMessage = true,
                        dmEnabled = state.dmEnabled,
                        onMessage = { cardPerson = null; startDirect(member.uid, member.name) },
                        onDismiss = { cardPerson = null }
                    )
                }
                when {
                    !canUse && state.approved == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    !canUse -> PendingApproval(onCheckAgain = { vm.start(uid, myName, isAdmin, isOwner) })
                    else -> Column(Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                SectionTabs(
                                    labels = listOf(
                                        stringResource(R.string.chat_tab_groups),
                                        stringResource(R.string.chat_tab_private),
                                        stringResource(R.string.chat_tab_people)
                                    ),
                                    selected = listTab,
                                    large = true,
                                    onSelect = { listTab = it }
                                )
                            }
                            // Leaders: approve people and manage the chat.
                            if (isAdmin) {
                                IconButton(onClick = { route = "admin" }) {
                                    Icon(
                                        Icons.Outlined.ManageAccounts,
                                        contentDescription = stringResource(R.string.chat_admin_open),
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        GroupList(
                            muted = state.muted,
                            uid = uid,
                            tab = listTab,
                            myRooms = state.myRooms,
                            people = state.directory,
                            loading = state.loading,
                            readTick = readTick,
                            isAdmin = isAdmin,
                            onOpen = { r -> roomId = r.id; route = "room" },
                            onDelete = { r -> pendingDelete = r },
                            onLeave = { r -> pendingLeave = r },
                            onPerson = { member -> cardPerson = member }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(accountMenu: @Composable () -> Unit) {
    AppHeader(accountMenu = accountMenu)
}

@Composable
private fun PendingApproval(onCheckAgain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.chat_pending_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.chat_pending_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onCheckAgain) { Text(stringResource(R.string.chat_check_again)) }
    }
}

@Composable
private fun GroupList(
    uid: String,
    tab: Int,
    myRooms: List<ChatRoom>,
    people: List<DirectoryMember>,
    loading: Boolean,
    readTick: Int,
    isAdmin: Boolean,
    muted: Set<String> = emptySet(),
    onOpen: (ChatRoom) -> Unit,
    onDelete: (ChatRoom) -> Unit,
    onLeave: (ChatRoom) -> Unit,
    onPerson: (DirectoryMember) -> Unit
) {
    if (loading) {
        SkeletonCards()
        return
    }
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    @Composable
    fun roomItem(room: ChatRoom) {
        val unread = remember(readTick, room.lastMessageAt, room.lastMessageBy) {
            val last = room.lastMessageAt?.time ?: 0L
            last > ChatReadStore.lastRead(context, uid, room.id) && room.lastMessageBy != uid
        }
        RoomCard(
            room = room,
            me = uid,
            unread = unread,
            muted = room.id in muted,
            canDelete = !room.direct && (isAdmin || room.createdBy == uid),
            canLeave = !room.direct && room.createdBy != uid,
            onDelete = { onDelete(room) },
            onLeave = { onLeave(room) },
            onClick = { onOpen(room) }
        )
    }

    @Composable
    fun emptyNote(title: Int, hint: Int) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    // How active the people are (looked up when the People tab is open).
    val presence by produceState(emptyMap<String, PresenceInfo>(), tab, people.size) {
        if (tab == 2) value = runCatching { PresenceStore.fetch(people.map { it.uid }) }.getOrDefault(emptyMap())
    }
    val groups = myRooms.filter { !it.direct }
    val directs = myRooms.filter { it.direct }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        when (tab) {
            0 -> {
                if (groups.isEmpty()) item { emptyNote(R.string.chat_empty_title, R.string.chat_empty_hint) }
                if (groups.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.chat_my_groups)) }
                    items(groups, key = { "m" + it.id }) { room -> roomItem(room) }
                }
            }
            1 -> {
                if (directs.isEmpty()) item { emptyNote(R.string.chat_private_empty_title, R.string.chat_private_empty_hint) }
                items(directs, key = { "p" + it.id }) { room -> roomItem(room) }
            }
            else -> {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        placeholder = { Text(stringResource(R.string.chat_search_people)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val shown = people.filter { it.uid != uid && (query.isBlank() || it.name.contains(query, ignoreCase = true)) }
                if (shown.isEmpty()) item { emptyNote(R.string.chat_people_empty, R.string.chat_people_empty_hint) }
                items(shown, key = { "u" + it.uid }) { member -> PersonRow(member = member, presence = presence[member.uid], onClick = { onPerson(member) }) }
            }
        }
    }
}

/** One person in the People list: tap to open their card. */
@Composable
private fun PersonRow(member: DirectoryMember, presence: PresenceInfo?, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonAvatar(uid = member.uid, name = member.name, size = 54.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    member.name.ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                PresenceLine(presence)
            }
        }
        ListDivider()
    }
}

/** A clear line under every chat (also under the last one), starting after the picture (like WhatsApp). */
@Composable
private fun ListDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.9f)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}


@Composable
private fun RoomCard(
    room: ChatRoom,
    me: String,
    unread: Boolean,
    muted: Boolean = false,
    canDelete: Boolean,
    canLeave: Boolean,
    onDelete: () -> Unit,
    onLeave: () -> Unit,
    onClick: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { if (canDelete || canLeave) menuOpen = true }
                    )
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (room.direct) {
                    PersonAvatar(uid = room.otherUid(me), name = room.otherName(me), size = 54.dp)
                } else {
                    GroupAvatar(room.name, room.color, 54.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        room.displayName(me),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val preview = when {
                        room.lastMessageText.isNotBlank() -> room.lastMessageText
                        room.description.isNotBlank() -> room.description
                        room.direct -> ""
                        else -> stringResource(R.string.chat_members_count, room.memberIds.size)
                    }
                    val youLabel = stringResource(R.string.chat_you)
                    val prefix = when {
                        room.lastMessageText.isBlank() -> ""
                        room.lastMessageBy == me -> "$youLabel: "
                        room.direct -> ""
                        else -> room.memberNames[room.lastMessageBy].orEmpty().substringBefore(' ')
                            .let { if (it.isBlank()) "" else "$it: " }
                    }
                    Text(
                        prefix + preview,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatWhen(room.lastActivity),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (muted) {
                        Icon(
                            Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp).size(16.dp)
                        )
                    }
                    if (unread) {
                        val unreadLabel = stringResource(R.string.chat_unread)
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .semantics { contentDescription = unreadLabel }
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (canLeave) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_leave)) },
                        onClick = { menuOpen = false; onLeave() }
                    )
                }
                if (canDelete) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_delete_group), color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
        ListDivider()
    }
}

/** Shown to admins when private messages are switched off, with a button to turn them back on. */
@Composable
private fun DmOffBanner(onTurnOn: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.dm_off_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onTurnOn) { Text(stringResource(R.string.dm_turn_on)) }
    }
}
