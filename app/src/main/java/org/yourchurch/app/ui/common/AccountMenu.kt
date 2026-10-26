package org.yourchurch.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yourchurch.app.R
import org.yourchurch.app.ShareApp
import org.yourchurch.app.data.AuthError
import org.yourchurch.app.data.AuthState
import org.yourchurch.app.data.ChurchInfoViewModel

/** The person picture in the top corner: opens the profile menu (Edit profile, Share the app, Sign out). */
@Composable
fun AccountMenu(
    auth: AuthState,
    onSaveProfile: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val infoVm: ChurchInfoViewModel = viewModel(key = "church-account")
    val appLink = infoVm.state.collectAsStateWithLifecycle().value.info.appLink
    LaunchedEffect(appLink) { ShareApp.rememberLink(context, appLink) }
    var showMenu by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val uid = auth.user?.uid.orEmpty()

    // The profile picture opens the profile menu. Everything else (language, notifications, privacy, deleting the
    // account) is under More.
    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(52.dp)) {
        AccountIcon(
            uid = uid,
            description = stringResource(R.string.profile_edit),
            size = 50.dp
        )
    }

    if (showMenu) {
        ProfileMenuDialog(
            uid = uid,
            name = auth.displayName,
            email = auth.user?.email.orEmpty(),
            onEdit = { showMenu = false; showEdit = true },
            onSignOut = { showMenu = false; confirmSignOut = true },
            onDismiss = { showMenu = false }
        )
    }
    if (showEdit) {
        EditProfileDialog(
            uid = uid,
            initialName = auth.displayName,
            initialPhone = auth.phone,
            email = auth.user?.email.orEmpty(),
            onSave = onSaveProfile,
            onDismiss = { showEdit = false }
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out)) },
            text = { Text(stringResource(R.string.sign_out_confirm)) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text(stringResource(R.string.sign_out)) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** The profile menu: the picture and name at the top, then Edit profile and Sign out. (Sharing the app is the long-press on the app icon.) */
@Composable
private fun ProfileMenuDialog(
    uid: String,
    name: String,
    email: String,
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name.ifBlank { stringResource(R.string.profile_edit) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    PersonAvatar(uid = uid, name = name, size = 72.dp, viewable = true)
                    Spacer(Modifier.width(16.dp))
                    Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProfileMenuRow(Icons.Outlined.Edit, stringResource(R.string.profile_edit_profile), onClick = onEdit)
                ProfileMenuRow(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.sign_out), MaterialTheme.colorScheme.error, onSignOut)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun ProfileMenuRow(icon: ImageVector, label: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = color)
    }
}

@Composable
internal fun DeleteAccountDialog(
    working: Boolean,
    error: AuthError?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.delete_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.delete_account_text))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.field_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                error?.let { Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty() && !working
            ) {
                Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.cancel)) }
        }
    )
}
