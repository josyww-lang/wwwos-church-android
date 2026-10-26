package org.yourchurch.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.yourchurch.app.R
import org.yourchurch.app.data.AuthViewModel
import org.yourchurch.app.data.normalizePhone
import org.yourchurch.app.ui.common.ChurchEmblem
import org.yourchurch.app.ui.common.ChurchName
import org.yourchurch.app.ui.common.InfoButton
import org.yourchurch.app.ui.common.LanguageDialog
import org.yourchurch.app.ui.theme.heroBrush

@Composable
fun SignInScreen(vm: AuthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showVisitor by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Blue header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroBrush(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .statusBarsPadding()
                // The logo is big (112) and its top edge sits a little lower (22 below the status bar), with a tighter
                // bottom edge (26), so the page is not stretched.
                .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ChurchEmblem(size = 112.dp)
            ChurchName(
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                textAlign = TextAlign.Center,
                startSize = 24f
            )
            Text(
                stringResource(if (creating) R.string.signup_subtitle else R.string.signin_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }

        // Form
        Column(
            // Close together from the email field down to the language button (the space between them was 16, then 10).
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.field_email)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            if (creating) {
                val phoneInvalid = phone.isNotBlank() && normalizePhone(phone) == null
                var showWhy by rememberSaveable { mutableStateOf(false) }      // why the phone number is asked: behind the "i"
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(25) },
                    label = { Text(stringResource(R.string.field_phone)) },
                    singleLine = true,
                    isError = phoneInvalid,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    trailingIcon = { InfoButton(shown = showWhy, onClick = { showWhy = !showWhy }) },
                    supportingText = if (phoneInvalid || showWhy) {
                        { Text(if (phoneInvalid) stringResource(R.string.err_phone_invalid) else stringResource(R.string.phone_why) + " " + stringResource(R.string.phone_hint)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.field_password)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.let {
                Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.resetSent) {
                Text(stringResource(R.string.reset_sent), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { vm.submit(email, password, creating, name, phone) },
                enabled = email.isNotBlank() && password.isNotEmpty() && (!creating || (name.isNotBlank() && normalizePhone(phone) != null)) && !state.working,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (state.working) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(if (creating) R.string.btn_create_account else R.string.btn_sign_in),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = { creating = !creating; vm.clearMessages() }) {
                    Text(stringResource(if (creating) R.string.link_have_account else R.string.link_create_account))
                }
                if (!creating) {
                    TextButton(onClick = { vm.resetPassword(email) }) {
                        Text(stringResource(R.string.link_forgot))
                    }
                }
            }

            // Visitors do not need an account: what to expect, how to find us, and a hello to the leaders.
            OutlinedButton(
                onClick = { showVisitor = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.home_explore_new))
            }

            TextButton(onClick = { showLanguage = true }) {
                Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.language))
            }
        }
    }

    if (showLanguage) LanguageDialog(onDismiss = { showLanguage = false })
    if (showVisitor) VisitorDialog(onDismiss = { showVisitor = false })
}
