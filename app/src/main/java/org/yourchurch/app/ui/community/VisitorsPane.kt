package org.yourchurch.app.ui.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.ChurchInfo
import org.yourchurch.app.data.ChurchInfoViewModel
import org.yourchurch.app.data.Visitor
import org.yourchurch.app.data.VisitorsViewModel

/** For leaders: the hellos from visitors, newest first. Tap a phone number or email to reach them. */
@Composable
fun VisitorsPane(
    vm: VisitorsViewModel = viewModel(key = "visitors-admin"),
    infoVm: ChurchInfoViewModel = viewModel(key = "church-visitors")
) {
    val infoState by infoVm.state.collectAsStateWithLifecycle()
    var editingText by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val visitors by vm.visitors.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Visitor?>(null) }
    LaunchedEffect(Unit) { vm.watch() }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            OutlinedButton(onClick = { editingText = true }, enabled = !infoState.loading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.visitors_edit_text))
            }
        }
        if (visitors.isEmpty()) {
            item {
                Text(stringResource(R.string.visitor_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(visitors, key = { it.id }) { visitor ->
            WordCard(onClick = {}) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(visitor.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        LeaderMenu(onEdit = null, onDelete = { deleting = visitor })
                    }
                    visitor.createdAt?.let {
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (visitor.phone.isNotBlank()) {
                        TextButton(onClick = { openDialer(context, visitor.phone) }, contentPadding = PaddingValues(0.dp)) { Text(visitor.phone) }
                    }
                    if (visitor.email.isNotBlank()) {
                        TextButton(onClick = { openMail(context, visitor.email) }, contentPadding = PaddingValues(0.dp)) { Text(visitor.email) }
                    }
                    if (visitor.message.isNotBlank()) {
                        Text(visitor.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Checkbox(checked = visitor.contacted, onCheckedChange = { vm.setContacted(visitor, it) })
                        Text(stringResource(R.string.visitor_contacted))
                    }
                }
            }
        }
    }
    if (editingText) {
        NewHereDialog(info = infoState.info, onSave = { infoVm.save(it) }, onDismiss = { editingText = false })
    }
    deleting?.let { visitor ->
        ConfirmDelete(onConfirm = { vm.delete(visitor); deleting = null }, onDismiss = { deleting = null })
    }
}


/** The welcome text that people see on the "I'm new here" page, in three languages. */
@Composable
private fun NewHereDialog(info: ChurchInfo, onSave: suspend (ChurchInfo) -> Result<Unit>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var en by rememberSaveable { mutableStateOf(info.newHereEn) }
    var nb by rememberSaveable { mutableStateOf(info.newHereNb) }
    var om by rememberSaveable { mutableStateOf(info.newHereOm) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.visitors_edit_text)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = en, onValueChange = { en = it.take(1500) }, minLines = 4, label = { Text(stringResource(R.string.contact_f_newhere_en)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nb, onValueChange = { nb = it.take(1500) }, minLines = 4, label = { Text(stringResource(R.string.contact_f_newhere_nb)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = om, onValueChange = { om = it.take(1500) }, minLines = 4, label = { Text(stringResource(R.string.contact_f_newhere_om)) }, modifier = Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.err_save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val result = onSave(info.copy(newHereEn = en.trim(), newHereNb = nb.trim(), newHereOm = om.trim()))
                    if (result.isSuccess) onDismiss() else failed = true
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
