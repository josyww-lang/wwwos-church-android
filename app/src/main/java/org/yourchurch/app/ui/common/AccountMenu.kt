package org.yourchurch.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.yourchurch.app.R
import org.yourchurch.app.data.AuthError
import org.yourchurch.app.data.AuthState

/** The person picture in the top corner: opens the profile (name, phone, picture) with the Sign out button. */
@Composable
fun AccountMenu(
    auth: AuthState,
    onSaveProfile: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    var showProfile by remember { mutableStateOf(false) }

    // The profile picture opens the profile. Everything else (language, notifications, privacy, deleting the account)
    // is under More.
    IconButton(onClick = { showProfile = true }, modifier = Modifier.size(52.dp)) {
        AccountIcon(
            uid = auth.user?.uid.orEmpty(),
            description = stringResource(R.string.profile_edit),
            size = 50.dp
        )
    }

    if (showProfile) {
        EditProfileDialog(
            uid = auth.user?.uid.orEmpty(),
            initialName = auth.displayName,
            initialPhone = auth.phone,
            email = auth.user?.email.orEmpty(),
            onSave = onSaveProfile,
            onDismiss = { showProfile = false },
            onSignOut = { showProfile = false; onSignOut() }
        )
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
