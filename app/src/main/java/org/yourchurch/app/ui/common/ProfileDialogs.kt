package org.yourchurch.app.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.CircularProgressIndicator
import org.yourchurch.app.data.EmailChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
    // Why the phone number is asked, and how to write it, are behind the "i": not shown all the time.
    var showWhy by remember { mutableStateOf(false) }
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
            trailingIcon = {
                IconButton(onClick = { showWhy = !showWhy }) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.profile_info))
                }
            },
            supportingText = if (phoneInvalid || showWhy) {
                { Text(if (phoneInvalid) stringResource(R.string.err_phone_invalid) else stringResource(R.string.phone_why) + " " + stringResource(R.string.phone_hint)) }
            } else null,
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
                InfoHint(stringResource(R.string.profile_text_complete))
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

/** Change my e-mail: the new address and the password; a confirmation link is sent to the new address. */
@Composable
fun ChangeEmailDialog(currentEmail: String, state: EmailChange, onSend: (String, String) -> Unit, onClose: () -> Unit) {
    var newEmail by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.email_change_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.sentTo != null) {
                    Text(stringResource(R.string.email_change_sent, state.sentTo))
                } else {
                    Text(currentEmail, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it.take(120) },
                        label = { Text(stringResource(R.string.email_change_new)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(100) },
                        label = { Text(stringResource(R.string.email_change_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let { Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            if (state.sentTo != null) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
            } else {
                TextButton(
                    onClick = { onSend(newEmail, password) },
                    enabled = !state.working && newEmail.isNotBlank() && password.isNotEmpty()
                ) {
                    if (state.working) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.email_change_send))
                }
            }
        },
        dismissButton = { if (state.sentTo == null) TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }
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
    onSignOut: (() -> Unit)? = null,
    /** When given, the profile has a "Change e-mail" button. */
    emailChange: EmailChange = EmailChange(),
    onChangeEmail: ((String, String) -> Unit)? = null,
    onEmailDialogClosed: () -> Unit = {}
) {
    var showEmail by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var phone by rememberSaveable { mutableStateOf(initialPhone) }
    val valid = name.isNotBlank() && (phone.isBlank() || normalizePhone(phone) != null)

    if (showEmail && onChangeEmail != null) {
        ChangeEmailDialog(
            currentEmail = email,
            state = emailChange,
            onSend = onChangeEmail,
            onClose = { showEmail = false; onEmailDialogClosed() }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfilePhotoSection(uid = uid, name = name)
                ProfileFields(name, { name = it }, phone, { phone = it }, email = email)
                if (onChangeEmail != null) {
                    TextButton(onClick = { showEmail = true }) { Text(stringResource(R.string.email_change_button)) }
                }
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
