package org.yourchurch.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yourchurch.app.LocaleManager
import org.yourchurch.app.R
import org.yourchurch.app.data.ChurchInfoViewModel
import org.yourchurch.app.data.VisitorsViewModel
import org.yourchurch.app.ui.common.InfoHint
import org.yourchurch.app.ui.community.openDialer
import org.yourchurch.app.ui.community.openDirections
import org.yourchurch.app.ui.community.openMail

/**
 * "I'm new here", shown from the sign-in screen so a visitor needs no account: what to expect, when and where we meet,
 * ways to call or find us, and a short form that sends a hello to the leaders.
 */
@Composable
fun VisitorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val infoVm: ChurchInfoViewModel = viewModel(key = "church-visitor")
    val info = infoVm.state.collectAsStateWithLifecycle().value.info
    val visitorVm: VisitorsViewModel = viewModel(key = "visitor-form")

    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val intro = info.newHereFor(LocaleManager.languageCode(context)).ifBlank { stringResource(R.string.newhere_default) }
    val contactMissing = phone.isBlank() && email.isBlank()
    val emailBad = email.isNotBlank() && !email.contains('@')
    val canSend = name.isNotBlank() && !contactMissing && !emailBad && !sending

    // Shown on top of the sign-in page in the same window (not as a separate dialog window), so the phone's bars and
    // keyboard are handled like on every other page and the Send button at the bottom is never hidden.
    BackHandler(onBack = onDismiss)
    run {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                    Text(stringResource(R.string.home_explore_new), style = MaterialTheme.typography.titleLarge)
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    VisitorCard { Text(intro, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(18.dp)) }

                    if (info.serviceTimes.isNotBlank()) {
                        VisitorCard {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.home_service_times), style = MaterialTheme.typography.titleSmall)
                                    Text(info.serviceTimes, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    if (info.address.isNotBlank()) {
                        Text(info.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickButton(Icons.Outlined.Directions, stringResource(R.string.home_directions), info.address.isNotBlank(), Modifier.weight(1f)) {
                            openDirections(context, info.address)
                        }
                        QuickButton(Icons.Outlined.Call, stringResource(R.string.home_call), info.phone.isNotBlank(), Modifier.weight(1f)) {
                            openDialer(context, info.phone)
                        }
                        QuickButton(Icons.Outlined.Email, stringResource(R.string.home_email), info.email.isNotBlank(), Modifier.weight(1f)) {
                            openMail(context, info.email)
                        }
                    }

                    VisitorCard {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.visitor_form_title), style = MaterialTheme.typography.titleMedium)
                            if (sent) {
                                Text(stringResource(R.string.visitor_thanks), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                            } else {
                                InfoHint(stringResource(R.string.visitor_form_hint), style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = name, onValueChange = { name = it.take(60) }, singleLine = true, enabled = !sending,
                                    label = { Text(stringResource(R.string.visitor_name)) }, modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = phone, onValueChange = { phone = it.take(30) }, singleLine = true, enabled = !sending,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    label = { Text(stringResource(R.string.visitor_phone)) }, modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = email, onValueChange = { email = it.take(120) }, singleLine = true, enabled = !sending,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), isError = emailBad,
                                    label = { Text(stringResource(R.string.visitor_email)) }, modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = message, onValueChange = { message = it.take(500) }, minLines = 3, enabled = !sending,
                                    label = { Text(stringResource(R.string.visitor_message)) }, modifier = Modifier.fillMaxWidth()
                                )
                                if (contactMissing && name.isNotBlank()) {
                                    Text(stringResource(R.string.visitor_need_contact), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                if (failed) {
                                    Text(stringResource(R.string.visitor_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                // The Send button stays at the bottom of the screen, always in view.
                if (!sent) {
                    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!canSend && !sending) {
                                Text(
                                    stringResource(R.string.visitor_send_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                enabled = canSend,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                onClick = {
                                    sending = true
                                    failed = false
                                    scope.launch {
                                        val result = visitorVm.send(name.trim(), phone.trim(), email.trim(), message.trim())
                                        sending = false
                                        if (result.isSuccess) sent = true else failed = true
                                    }
                                }
                            ) {
                                if (sending) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Text(stringResource(R.string.visitor_send), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitorCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
private fun QuickButton(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
    }
}
