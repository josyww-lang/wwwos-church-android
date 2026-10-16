package org.yourchurch.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.yourchurch.app.Notifications
import org.yourchurch.app.R
import org.yourchurch.app.data.NotificationPrefs
import org.yourchurch.app.data.PushTokens

/**
 * Which notifications to get (chat, announcements, events, live services) and quiet hours at night.
 * Urgent announcements are the one exception to quiet hours.
 */
@Composable
fun NotificationSettingsDialog(uid: String, onCheckPhone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chat by remember { mutableStateOf(NotificationPrefs.chat) }
    var announcements by remember { mutableStateOf(NotificationPrefs.announcements) }
    var events by remember { mutableStateOf(NotificationPrefs.events) }
    var live by remember { mutableStateOf(NotificationPrefs.live) }
    var sermons by remember { mutableStateOf(NotificationPrefs.sermons) }
    var giving by remember { mutableStateOf(NotificationPrefs.giving) }
    var reminders by remember { mutableStateOf(NotificationPrefs.reminders) }
    var masterOn by remember { mutableStateOf(Notifications.isEnabled(context)) }
    var quietOn by remember { mutableStateOf(NotificationPrefs.quietOn) }
    var quietFrom by remember { mutableStateOf(NotificationPrefs.quietFrom) }
    var quietTo by remember { mutableStateOf(NotificationPrefs.quietTo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_settings_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // The main switch: notifications on this phone at all.
                ToggleRow(stringResource(R.string.notif_master), masterOn) { on ->
                    masterOn = on
                    Notifications.setEnabled(context, on)
                    Notifications.sync(context, signedIn = true)
                    scope.launch { runCatching { if (on) PushTokens.register(uid) else PushTokens.unregister(uid) } }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(stringResource(R.string.notif_type_chat), chat) { chat = it }
                ToggleRow(stringResource(R.string.notif_type_announcements), announcements) { announcements = it }
                ToggleRow(stringResource(R.string.notif_type_events), events) { events = it }
                ToggleRow(stringResource(R.string.notif_type_reminders), reminders) { reminders = it }
                ToggleRow(stringResource(R.string.notif_type_sermons), sermons) { sermons = it }
                ToggleRow(stringResource(R.string.notif_type_giving), giving) { giving = it }
                ToggleRow(stringResource(R.string.notif_type_live), live) { live = it }

                Spacer(Modifier.height(12.dp))
                ToggleRow(stringResource(R.string.notif_quiet), quietOn) { quietOn = it }
                Text(
                    stringResource(R.string.notif_quiet_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HourPicker(stringResource(R.string.notif_quiet_from), quietFrom, quietOn) { quietFrom = it }
                    Spacer(Modifier.width(12.dp))
                    HourPicker(stringResource(R.string.notif_quiet_to), quietTo, quietOn) { quietTo = it }
                }

            }
        },
        confirmButton = {
            TextButton(onClick = {
                NotificationPrefs.save(context, chat, announcements, events, live, sermons, giving, reminders, quietOn, quietFrom, quietTo)
                scope.launch { runCatching { NotificationPrefs.sync(uid) } }
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun HourPicker(label: String, hour: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled) {
            Text(label + " " + String.format(java.util.Locale.getDefault(), "%02d:00", hour))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (0..23).forEach { h ->
                DropdownMenuItem(
                    text = { Text(String.format(java.util.Locale.getDefault(), "%02d:00", h)) },
                    onClick = { onChange(h); open = false }
                )
            }
        }
    }
}
