@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.chat

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.yourchurch.app.R
import org.yourchurch.app.data.ChatRoom
import org.yourchurch.app.data.ChatState
import org.yourchurch.app.data.ChatViewModel
import org.yourchurch.app.ui.common.PersonAvatar

/**
 * Opens when you tap the group's name. Everyone in the group, each with a labelled Message button and
 * Call button. Tap a row to open the person's card. The Call button works for people who chose to share
 * their number with you.
 */
@Composable
fun MembersSheet(
    room: ChatRoom,
    myUid: String,
    isModerator: Boolean,
    state: ChatState,
    dmEnabled: Boolean,
    vm: ChatViewModel,
    onMessage: (String, String) -> Unit,
    onOpenCard: (String, String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val members = room.memberIds.sortedWith(
        compareBy({ it != room.createdBy }, { it != myUid }, { room.memberNames[it]?.lowercase() ?: "" })
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${stringResource(R.string.chat_members)} (${room.memberIds.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                if (isModerator) {
                    TextButton(onClick = onAdd) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.chat_add_people))
                    }
                }
            }

            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(members, key = { it }) { memberUid ->
                    MemberRow(
                        room = room,
                        memberUid = memberUid,
                        myUid = myUid,
                        isModerator = isModerator,
                        phone = state.roomPhones[memberUid].orEmpty(),
                        dmEnabled = dmEnabled,
                        onMessage = onMessage,
                        onOpenCard = onOpenCard,
                        onRemove = { vm.removeMember(room, memberUid) }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MemberRow(
    room: ChatRoom,
    memberUid: String,
    myUid: String,
    isModerator: Boolean,
    phone: String,
    dmEnabled: Boolean,
    onMessage: (String, String) -> Unit,
    onOpenCard: (String, String) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val name = room.memberNames[memberUid].orEmpty()
    val isMe = memberUid == myUid
    val notSharedText = stringResource(R.string.member_call_not_shared, name)
    val dmOffText = stringResource(R.string.dm_disabled_note)
    var menuOpen by remember { mutableStateOf(false) }
    val canCall = phone.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenCard(memberUid, name) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        PersonAvatar(uid = memberUid, name = name, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column {
                Text(
                    name.ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when {
                    isMe -> R.string.chat_you
                    memberUid == room.createdBy -> R.string.chat_owner
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        stringResource(subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isMe) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    val messageColor = if (dmEnabled) MaterialTheme.colorScheme.primary else muted
                    // Message: always shown. If an admin has switched private messages off, it explains why.
                    AssistChip(
                        onClick = {
                            if (dmEnabled) onMessage(memberUid, name)
                            else Toast.makeText(context, dmOffText, Toast.LENGTH_SHORT).show()
                        },
                        label = { Text(stringResource(R.string.person_message)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = messageColor,
                            leadingIconContentColor = messageColor
                        )
                    )
                    AssistChip(
                        onClick = {
                            if (canCall) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
                                }
                            } else {
                                Toast.makeText(context, notSharedText, Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text(stringResource(R.string.person_call)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = if (canCall) MaterialTheme.colorScheme.primary else muted,
                            leadingIconContentColor = if (canCall) MaterialTheme.colorScheme.primary else muted
                        )
                    )
                }
            }
        }
        if (isModerator && !isMe && memberUid != room.createdBy) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_remove), color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onRemove() }
                    )
                }
            }
        }
    }
}
