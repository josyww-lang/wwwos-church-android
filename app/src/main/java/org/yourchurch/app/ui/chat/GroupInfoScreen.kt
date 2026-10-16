@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.chat

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.yourchurch.app.R
import org.yourchurch.app.data.ChatRoom
import org.yourchurch.app.data.ChatState
import org.yourchurch.app.data.ChatViewModel
import org.yourchurch.app.ui.common.PersonAvatar
import org.yourchurch.app.ui.theme.GROUP_COLORS
import org.yourchurch.app.ui.theme.groupColor

@Composable
fun GroupInfoScreen(
    room: ChatRoom,
    myUid: String,
    myPhone: String,
    isAdmin: Boolean,
    isModerator: Boolean,
    state: ChatState,
    vm: ChatViewModel,
    dmEnabled: Boolean,
    onMessageMember: (String, String) -> Unit,
    onBack: () -> Unit,
    onLeftOrDeleted: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_info), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(room.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(if (room.open) R.string.chat_type_open else R.string.chat_type_private),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (room.description.isNotBlank()) {
                Text(room.description, style = MaterialTheme.typography.bodyLarge)
            }

            Text(
                "${stringResource(R.string.chat_members)} (${room.memberIds.size})",
                style = MaterialTheme.typography.titleMedium
            )

            room.memberIds
                .sortedWith(compareBy({ it != room.createdBy }, { room.memberNames[it]?.lowercase() ?: "" }))
                .forEach { memberUid ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PersonAvatar(uid = memberUid, name = room.memberNames[memberUid].orEmpty(), size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(room.memberNames[memberUid]?.ifBlank { "?" } ?: "?")
                            if (memberUid == room.createdBy) {
                                Text(
                                    stringResource(R.string.chat_owner),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Send this person a private message. Always shown; explains if an admin switched it off.
                        if (memberUid != myUid) {
                            val memberLabel = room.memberNames[memberUid].orEmpty()
                            val messageDescription = stringResource(R.string.dm_send_to, memberLabel)
                            val dmOffText = stringResource(R.string.dm_disabled_note)
                            IconButton(
                                onClick = {
                                    if (dmEnabled) onMessageMember(memberUid, memberLabel)
                                    else Toast.makeText(context, dmOffText, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.semantics { contentDescription = messageDescription }
                            ) {
                                Icon(
                                    Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (dmEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // A leader can ring people who chose to share their number with this group.
                        val sharedPhone = state.roomPhones[memberUid].orEmpty()
                        if (memberUid != myUid && sharedPhone.isNotBlank()) {
                            val memberName = room.memberNames[memberUid].orEmpty()
                            val callDescription = stringResource(R.string.call_person, memberName)
                            IconButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", sharedPhone, null))
                                        )
                                    }
                                },
                                modifier = Modifier.semantics { contentDescription = callDescription }
                            ) {
                                Icon(Icons.Outlined.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (isModerator && memberUid != room.createdBy && memberUid != myUid) {
                            TextButton(onClick = { vm.removeMember(room, memberUid) }) {
                                Text(stringResource(R.string.chat_remove), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

            // Group colour: anyone in the group can change it. It colours the group's badge and your messages in it.
            if (myUid in room.memberIds) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.group_color), style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GROUP_COLORS.indices.forEach { i ->
                            val selected = room.color == i
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(groupColor(i), CircleShape)
                                    .then(
                                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { vm.setGroupColor(room, i) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Mute: no notifications from this group (new messages still show inside the app).
            if (myUid in room.memberIds) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.chat_mute), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.chat_muted_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = room.id in state.muted, onCheckedChange = { vm.toggleMute(room.id) })
                }
            }

            // Choose who can see my phone number in this group.
            if (myUid in room.memberIds) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.share_phone), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (myPhone.isBlank()) R.string.share_phone_need else R.string.share_phone_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val options = if (myUid == room.createdBy) {
                        listOf("" to R.string.share_none, "group" to R.string.share_group)
                    } else {
                        listOf("" to R.string.share_none, "leaders" to R.string.share_leaders, "group" to R.string.share_group)
                    }
                    options.forEach { (value, label) ->
                        val enabled = myPhone.isNotBlank() || value.isEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.myShare == value,
                                    enabled = enabled,
                                    role = Role.RadioButton,
                                    onClick = { vm.setShare(room, value, myPhone) }
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = state.myShare == value, onClick = null, enabled = enabled)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(label))
                        }
                    }
                }
            }

            if (isModerator) {
                OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.chat_add_people))
                }
            }

            if (myUid != room.createdBy && myUid in room.memberIds) {
                TextButton(onClick = { confirmLeave = true }) {
                    Text(stringResource(R.string.chat_leave), color = MaterialTheme.colorScheme.error)
                }
            }
            if (isModerator) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.chat_delete_group), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.chat_leave_title)) },
            text = { Text(room.name) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.leave(room)
                    onLeftOrDeleted()
                }) { Text(stringResource(R.string.chat_leave), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.chat_delete_group_title)) },
            text = { Text("${room.name}\n\n${stringResource(R.string.chat_delete_group_text)}") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteRoom(room)
                    onLeftOrDeleted()
                }) { Text(stringResource(R.string.chat_delete_group), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
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
