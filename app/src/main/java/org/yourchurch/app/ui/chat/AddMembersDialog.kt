package org.yourchurch.app.ui.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.yourchurch.app.R
import org.yourchurch.app.data.ChatRoom
import org.yourchurch.app.data.ChatState
import org.yourchurch.app.data.ChatViewModel

/** Pick approved members to add to a group. Admins can approve waiting people from here too. */
@Composable
fun AddMembersDialog(
    room: ChatRoom,
    myUid: String,
    isAdmin: Boolean,
    state: ChatState,
    vm: ChatViewModel,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    val candidates = state.directory.filter { it.uid !in room.memberIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_add_people)) },
        text = {
            MemberPicker(
                candidates = candidates,
                selected = selected,
                onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                loaded = state.directoryLoaded,
                failed = state.directoryFailed,
                isAdmin = isAdmin,
                pending = state.people.filter { !it.approved && it.uid != myUid },
                onApprove = { vm.approve(it) }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chosen = candidates.filter { it.uid in selected }.associate { it.uid to it.name }
                    vm.addMembers(room, chosen)
                    onDismiss()
                },
                enabled = selected.isNotEmpty()
            ) { Text(stringResource(R.string.chat_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
