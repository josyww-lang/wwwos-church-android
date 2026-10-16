@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.ChatState
import org.yourchurch.app.data.ChatViewModel

/** Anyone approved can create a group at any time. */
@Composable
fun CreateGroupScreen(
    uid: String,
    isAdmin: Boolean,
    state: ChatState,
    vm: ChatViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    // Open (leaders only): everyone in the church is added automatically. Invite-only: the person making the group picks
    // the people. Nobody can join on their own.
    var open by rememberSaveable { mutableStateOf(false) }
    var channel by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var saving by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = !saving, onBack = onBack)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_new_group), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(stringResource(R.string.chat_group_name)) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(200) },
                label = { Text(stringResource(R.string.chat_group_description)) },
                minLines = 2,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )

            // Only a leader can make an open group (everyone in the church is in it).
            if (isAdmin) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(if (open) R.string.chat_group_open else R.string.chat_group_invite_only),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Switch(checked = open, onCheckedChange = { open = it }, enabled = !saving && !channel)
                }
            }

            // Leaders can make an announcement channel: everyone reads, only leaders post.
            if (isAdmin) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.chat_channel_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.chat_channel_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = channel, onCheckedChange = { channel = it; if (it) open = true }, enabled = !saving)
                }
            }

            if (!open) {
                Text(stringResource(R.string.chat_pick_people), style = MaterialTheme.typography.titleMedium)
                MemberPicker(
                    candidates = state.directory.filter { it.uid != uid },
                    selected = selected,
                    onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                    loaded = state.directoryLoaded,
                    failed = state.directoryFailed,
                    isAdmin = isAdmin,
                    pending = state.people.filter { !it.approved && it.uid != uid },
                    onApprove = { vm.approve(it) }
                )
            }

            errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    if (name.trim().length < 2) {
                        errorRes = R.string.chat_name_too_short
                        return@Button
                    }
                    errorRes = null
                    saving = true
                    val invited = if (open) emptyMap() else
                        state.directory.filter { it.uid in selected }.associate { it.uid to it.name }
                    scope.launch {
                        vm.createRoom(name.trim(), description.trim(), open, invited, channel)
                            .onSuccess { id -> saving = false; onCreated(id) }
                            .onFailure { saving = false; errorRes = R.string.chat_create_failed }
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.chat_create), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
