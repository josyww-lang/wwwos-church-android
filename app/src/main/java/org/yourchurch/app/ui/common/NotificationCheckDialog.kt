package org.yourchurch.app.ui.common

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import org.yourchurch.app.Notifications
import org.yourchurch.app.R
import org.yourchurch.app.data.PushTokens

/**
 * Tells you, in plain words, whether this phone can receive notifications, and lets you fix the usual
 * problems and send yourself a test.
 */
@Composable
fun NotificationCheckDialog(uid: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var switchOn by remember { mutableStateOf(Notifications.isEnabled(context)) }
    var registered by remember { mutableStateOf<Boolean?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    // Coming back from the phone's settings: look again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        switchOn = Notifications.isEnabled(context)
        refresh++
    }
    LaunchedEffect(uid, refresh) {
        registered = null
        registered = runCatching { PushTokens.isRegistered(uid) }.getOrDefault(false)
    }

    val testTitle = stringResource(R.string.notif_check_test_title)
    val testBody = stringResource(R.string.notif_check_test_body)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_check_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CheckLine(allowed, stringResource(R.string.notif_check_allowed))
                CheckLine(switchOn, stringResource(R.string.notif_check_switch))
                CheckLine(registered, stringResource(R.string.notif_check_registered))

                if (!allowed) {
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.notif_check_allow)) }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.notif_check_settings)) }
                }
                if (registered == false || !switchOn) {
                    OutlinedButton(
                        onClick = {
                            Notifications.setEnabled(context, true)
                            switchOn = true
                            Notifications.sync(context, signedIn = true)
                            scope.launch {
                                runCatching { PushTokens.register(uid) }
                                refresh++
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.notif_check_register)) }
                }
                OutlinedButton(
                    onClick = { Notifications.show(context, testTitle, testBody, roomId = "test") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.notif_check_test)) }
                InfoHint(stringResource(R.string.notif_check_hint))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

/** A tick, a cross, or a spinner (when [ok] is null) next to a line of text. */
@Composable
private fun CheckLine(ok: Boolean?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (ok) {
            null -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            true -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp))
            false -> Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
