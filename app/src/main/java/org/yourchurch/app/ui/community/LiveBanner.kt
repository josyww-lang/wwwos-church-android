package org.yourchurch.app.ui.community

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.LiveInfo
import org.yourchurch.app.data.LiveViewModel
import org.yourchurch.app.ui.common.InfoHint

/**
 * A red "LIVE NOW" banner on Home while the service is streaming (nothing is shown the rest of the time).
 * Leaders set it by hand, for a Facebook live or anything else, under More > Live broadcast.
 */
@Composable
fun LiveBanner(
    uid: String,
    isAdmin: Boolean,
    outerPadding: Dp = 16.dp,
    vm: LiveViewModel = viewModel(key = "live-$uid")
) {
    val info by vm.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    val on = info.isOn()

    if (on) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = outerPadding)
                .background(Color(0xFFC62828), RoundedCornerShape(14.dp))
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(8.dp).background(Color.White, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.live_now) + if (info.title.isNotBlank()) " \u00B7 ${info.title}" else "",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url))) } }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.live_watch), color = Color(0xFFC62828), style = MaterialTheme.typography.titleMedium)
            }
            if (isAdmin) {
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.live_settings), tint = Color.White)
                }
            }
        }
    }

    if (showSettings) {
        LiveSettingsDialog(info = info, vm = vm, onDismiss = { showSettings = false })
    }
}

@Composable
internal fun LiveSettingsDialog(info: LiveInfo, vm: LiveViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var live by remember { mutableStateOf(info.isOn()) }
    var title by remember { mutableStateOf(info.title) }
    var url by remember { mutableStateOf(info.url) }
    var error by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.live_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoHint(stringResource(R.string.live_help))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.live_show), modifier = Modifier.weight(1f))
                    Switch(checked = live, onCheckedChange = { live = it })
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text(stringResource(R.string.live_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.take(300); error = false },
                    label = { Text(stringResource(R.string.live_url_hint)) },
                    singleLine = true,
                    isError = error,
                    supportingText = { if (error) Text(stringResource(R.string.live_url_invalid)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    if (live && !url.trim().startsWith("https://")) {
                        error = true
                    } else {
                        saving = true
                        scope.launch {
                            vm.save(live, url, title)
                            saving = false
                            onDismiss()
                        }
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
