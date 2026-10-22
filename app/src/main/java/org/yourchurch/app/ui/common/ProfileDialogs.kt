package org.yourchurch.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.yourchurch.app.R
import org.yourchurch.app.data.normalizePhone

@Composable
private fun ProfileFields(
    name: String,
    onName: (String) -> Unit,
    phone: String,
    onPhone: (String) -> Unit,
    email: String?
) {
    val phoneInvalid = phone.isNotBlank() && normalizePhone(phone) == null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { onName(it.take(60)) },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidthCompat()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { onPhone(it.take(25)) },
            label = { Text(stringResource(R.string.field_phone)) },
            singleLine = true,
            isError = phoneInvalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            supportingText = {
                Text(stringResource(if (phoneInvalid) R.string.err_phone_invalid else R.string.phone_hint))
            },
            modifier = Modifier.fillMaxWidthCompat()
        )
        if (!email.isNullOrEmpty()) {
            OutlinedTextField(
                value = email,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.field_email)) },
                modifier = Modifier.fillMaxWidthCompat()
            )
        }
    }
}

private fun Modifier.fillMaxWidthCompat(): Modifier = this.then(Modifier.fillMaxWidth())

/**
 * Shown to people whose profile is missing a name (they must add one), or a phone number
 * (they may tap "Not now"). Cannot be dismissed by tapping outside.
 */
@Composable
fun CompleteProfileDialog(
    initialName: String,
    initialPhone: String,
    onSave: (String, String) -> Unit,
    onSkipPhone: () -> Unit,
    onSignOut: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var phone by rememberSaveable { mutableStateOf(initialPhone) }
    val valid = name.isNotBlank() && (phone.isBlank() || normalizePhone(phone) != null)

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.profile_title_complete)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.profile_text_complete))
                Text(
                    stringResource(R.string.phone_why),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ProfileFields(name, { name = it }, phone, { phone = it }, email = null)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, phone) }, enabled = valid) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            if (initialName.isNotBlank()) {
                TextButton(onClick = onSkipPhone) { Text(stringResource(R.string.skip)) }
            } else {
                TextButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
            }
        }
    )
}

/** "My profile" from the account menu: change name and phone number. */
@Composable
fun EditProfileDialog(
    uid: String,
    initialName: String,
    initialPhone: String,
    email: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
    /** When given, the profile has a Sign out button. */
    onSignOut: (() -> Unit)? = null
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var phone by rememberSaveable { mutableStateOf(initialPhone) }
    val valid = name.isNotBlank() && (phone.isBlank() || normalizePhone(phone) != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfilePhotoSection(uid = uid, name = name)
                Text(
                    stringResource(R.string.phone_why),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ProfileFields(name, { name = it }, phone, { phone = it }, email = email)
                ActivityStatusPicker()
                if (onSignOut != null) {
                    TextButton(onClick = { confirmSignOut = true }) {
                        Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name, phone)
                    onDismiss()
                },
                enabled = valid
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (confirmSignOut && onSignOut != null) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out)) },
            text = { Text(stringResource(R.string.sign_out_confirm)) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text(stringResource(R.string.sign_out)) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
