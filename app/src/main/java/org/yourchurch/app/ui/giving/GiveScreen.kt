@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.giving

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.GivingInfo
import org.yourchurch.app.data.GivingViewModel

private const val VIPPS_PACKAGE = "no.dnb.vipps"

@Composable
fun GiveScreen(
    uid: String,
    isAdmin: Boolean,
    vm: GivingViewModel = viewModel(key = uid)
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    val info = state.info

    if (editing) {
        GivingEditScreen(initial = info, onSave = vm::save, onClose = { editing = false })
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.give_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.give_edit))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.give_thanks),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                state.loading -> CircularProgressIndicator()
                state.loadFailed -> Text(stringResource(R.string.load_failed), color = MaterialTheme.colorScheme.error)
                !info.hasVipps && !info.hasBank -> Text(
                    stringResource(if (isAdmin) R.string.give_not_set_admin else R.string.give_not_set_member),
                    style = MaterialTheme.typography.bodyLarge
                )
                else -> {
                    if (info.hasVipps) VippsCard(info)
                    if (info.hasBank) BankCard(info)
                    if (info.note.isNotBlank()) Text(info.note, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // Below the bank details: a way to send the leaders a message without a name.
        }
    }
}

@Composable
private fun VippsCard(info: GivingInfo) {
    val context = LocalContext.current
    GiveCard(title = stringResource(R.string.give_vipps)) {
        CopyRow(label = stringResource(R.string.give_vipps_number), value = info.vippsNumber, big = true)
        Text(
            stringResource(R.string.give_vipps_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = { openVipps(context) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text(stringResource(R.string.give_open_vipps), fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun BankCard(info: GivingInfo) {
    GiveCard(title = stringResource(R.string.give_bank)) {
        if (info.accountName.isNotBlank()) {
            LabelValue(stringResource(R.string.give_account_holder), info.accountName)
        }
        if (info.accountNumber.isNotBlank()) {
            CopyRow(stringResource(R.string.give_account_number), info.accountNumber)
        }
        if (info.iban.isNotBlank()) {
            CopyRow(stringResource(R.string.give_iban), info.iban)
        }
    }
}

@Composable
private fun GiveCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CopyRow(label: String, value: String, big: Boolean = false) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.give_copied)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                fontWeight = if (big) FontWeight.Medium else FontWeight.Normal
            )
        }
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.give_copy))
        }
    }
}

/** Opens the Vipps app if it's installed, otherwise its Google Play page. */
private fun openVipps(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(VIPPS_PACKAGE)
    val intent = launch ?: Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$VIPPS_PACKAGE")
    )
    runCatching { context.startActivity(intent) }
}

/** Admin-only form for entering the church's Vipps number and bank details. */
@Composable
private fun GivingEditScreen(
    initial: GivingInfo,
    onSave: suspend (GivingInfo) -> Result<Unit>,
    onClose: () -> Unit
) {
    var vipps by rememberSaveable { mutableStateOf(initial.vippsNumber) }
    var holder by rememberSaveable { mutableStateOf(initial.accountName) }
    var account by rememberSaveable { mutableStateOf(initial.accountNumber) }
    var iban by rememberSaveable { mutableStateOf(initial.iban) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onClose)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.give_edit), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = vipps, onValueChange = { vipps = it.trim().take(20) },
                label = { Text(stringResource(R.string.give_vipps_number)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = holder, onValueChange = { holder = it.take(80) },
                label = { Text(stringResource(R.string.give_account_holder)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = account, onValueChange = { account = it.take(20) },
                label = { Text(stringResource(R.string.give_account_number)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = iban, onValueChange = { iban = it.take(40) },
                label = { Text(stringResource(R.string.give_iban)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(500) },
                label = { Text(stringResource(R.string.give_note)) },
                minLines = 3, modifier = Modifier.fillMaxWidth()
            )

            if (failed) Text(stringResource(R.string.err_save_failed), color = MaterialTheme.colorScheme.error)

            Button(
                onClick = {
                    failed = false
                    saving = true
                    scope.launch {
                        val result = onSave(
                            GivingInfo(
                                vippsNumber = vipps.trim(),
                                accountName = holder.trim(),
                                accountNumber = account.trim(),
                                iban = iban.trim(),
                                note = note.trim()
                            )
                        )
                        saving = false
                        result.onSuccess { onClose() }.onFailure { failed = true }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.save))
            }
        }
    }
}

/** Anyone can send the leaders a message here. Their name and account are not saved with it. */
