package org.yourchurch.app.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.BrandingStore

/** For leaders: choose the church logo from the phone's pictures. It then shows at the top of every page. */
@Composable
fun LogoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            message = null
            scope.launch {
                val ok = BrandingStore.save(context, uri)
                busy = false
                message = if (ok) R.string.logo_saved else R.string.logo_failed
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logo_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ChurchLogo(size = 96.dp)
                InfoHint(stringResource(R.string.logo_hint), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                message?.let {
                    Text(
                        stringResource(it),
                        color = if (it == R.string.logo_saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                if (BrandingStore.logo != null) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val ok = BrandingStore.remove()
                                busy = false
                                message = if (ok) R.string.logo_saved else R.string.logo_failed
                            }
                        }
                    ) { Text(stringResource(R.string.logo_remove), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            ) { Text(stringResource(R.string.logo_choose)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
