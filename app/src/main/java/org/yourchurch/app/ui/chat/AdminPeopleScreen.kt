@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.chat

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.AvatarStore
import org.yourchurch.app.data.ChatState
import org.yourchurch.app.data.ChatViewModel
import org.yourchurch.app.data.PersonRow
import org.yourchurch.app.ui.common.PersonAvatar

/** Admin only: approve people for chat, and review reported messages. */
@Composable
fun AdminPeopleScreen(
    state: ChatState,
    vm: ChatViewModel,
    myUid: String,
    onMessage: (String, String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pending = state.people.filter { !it.approved }
    val approved = state.people.filter { it.approved }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_admin_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("${stringResource(R.string.chat_people)} (${pending.size})") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("${stringResource(R.string.chat_reports)} (${state.reports.size})") }
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (tab == 0) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(stringResource(R.string.dm_setting_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.dm_setting_text),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = state.dmEnabled, onCheckedChange = { vm.setDmEnabled(it) })
                        }
                    }
                    item {
                        Text(stringResource(R.string.chat_pending_people), style = MaterialTheme.typography.titleMedium)
                    }
                    if (pending.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.chat_no_pending),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(pending, key = { "p" + it.uid }) { person -> PersonCard(person, vm, myUid, state.dmEnabled, onMessage) }

                    item {
                        Text(
                            stringResource(R.string.chat_approved_people),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    items(approved, key = { "a" + it.uid }) { person -> PersonCard(person, vm, myUid, state.dmEnabled, onMessage) }
                } else {
                    if (state.reports.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.chat_no_reports),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(state.reports, key = { it.id }) { report ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    stringResource(R.string.chat_report_from, report.senderName, report.roomName),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(report.messageText, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.chat_reported_by, report.reporterName),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vm.removeReportedMessage(report) }) {
                                        Text(stringResource(R.string.chat_delete_message), color = MaterialTheme.colorScheme.error)
                                    }
                                    TextButton(onClick = { vm.dismissReport(report) }) {
                                        Text(stringResource(R.string.chat_dismiss))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: PersonRow,
    vm: ChatViewModel,
    myUid: String,
    dmEnabled: Boolean,
    onMessage: (String, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PersonAvatar(uid = person.uid, name = person.name, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(person.name.ifBlank { "?" }, style = MaterialTheme.typography.titleMedium)
                if (AvatarStore.bitmaps.containsKey(person.uid)) {
                    val scope = rememberCoroutineScope()
                    TextButton(onClick = { scope.launch { AvatarStore.delete(person.uid) } }) {
                        Text(stringResource(R.string.photo_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (person.email.isNotBlank()) {
                    Text(
                        person.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (person.phone.isNotBlank()) {
                    Text(
                        person.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (person.phone.isNotBlank()) {
                val context = LocalContext.current
                val callDescription = stringResource(R.string.call_person, person.name)
                IconButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", person.phone, null)))
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = callDescription }
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (person.approved && person.uid != myUid) {
                val messageContext = LocalContext.current
                val messageDescription = stringResource(R.string.dm_send_to, person.name)
                val dmOffText = stringResource(R.string.dm_disabled_note)
                IconButton(
                    onClick = {
                        if (dmEnabled) onMessage(person.uid, person.name)
                        else Toast.makeText(messageContext, dmOffText, Toast.LENGTH_SHORT).show()
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
            if (person.approved) {
                TextButton(onClick = { vm.revoke(person) }) {
                    Text(stringResource(R.string.chat_revoke), color = MaterialTheme.colorScheme.error)
                }
            } else {
                Button(onClick = { vm.approve(person) }, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.chat_approve))
                }
            }
        }
    }
}
