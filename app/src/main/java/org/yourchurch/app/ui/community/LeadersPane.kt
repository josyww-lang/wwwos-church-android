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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.yourchurch.app.R
import org.yourchurch.app.data.LeaderRow
import org.yourchurch.app.data.LeadersAdminViewModel

private fun shortDate(date: Date): String = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(date)

/**
 * Team and access: what each member can do. A member can be a study contributor (adds lessons, files and plans), or a
 * leader (does what an owner does in the app, for a term or with no end date). Only owners can make leaders.
 */
@Composable
fun LeadersPane(isOwner: Boolean, vm: LeadersAdminViewModel = viewModel(key = "leaders-admin")) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<LeaderRow?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                stringResource(R.string.roles_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        if (rows.isEmpty()) {
            item { Text(stringResource(R.string.leaders_empty), style = MaterialTheme.typography.bodyLarge) }
        }
        items(rows, key = { it.uid }) { row ->
            WordCard(onClick = { editing = row }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(row.name.ifBlank { "?" }, style = MaterialTheme.typography.titleMedium)
                        val active = row.isLeader && !row.expired
                        val status = when {
                            row.expired -> stringResource(R.string.leaders_ended, shortDate(row.until!!))
                            active && row.until != null -> stringResource(R.string.leaders_until, shortDate(row.until))
                            active -> stringResource(R.string.leaders_open)
                            row.isContributor -> stringResource(R.string.roles_contributor)
                            else -> stringResource(R.string.leaders_member)
                        }
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active || row.isContributor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    editing?.let { row ->
        RoleDialog(
            row = row,
            isOwner = isOwner,
            onSave = { role, until -> vm.setRole(row, role, until); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun RoleOption(selected: Boolean, enabled: Boolean, title: String, description: String?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        RadioButton(selected = selected, enabled = enabled, onClick = onClick)
        Column(Modifier.padding(top = 12.dp, start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Choose the role; a leader can have an end date. */
@Composable
private fun RoleDialog(row: LeaderRow, isOwner: Boolean, onSave: (Int, Date?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var role by remember { mutableIntStateOf(if (row.isLeader && !row.expired) 2 else if (row.isContributor) 1 else 0) }
    var noEnd by remember { mutableStateOf(row.until == null) }
    var endMillis by remember {
        mutableLongStateOf(
            row.until?.takeIf { it.after(Date()) }?.time ?: Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
        )
    }
    // Only an owner can make, change or end a leader.
    val locked = !isOwner && (row.isLeader || role == 2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leaders_dialog_title, row.name.ifBlank { "?" })) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RoleOption(role == 0, !(locked && row.isLeader), stringResource(R.string.leaders_member), null) { role = 0 }
                RoleOption(role == 1, !(locked && row.isLeader), stringResource(R.string.roles_contributor), stringResource(R.string.roles_contributor_desc)) { role = 1 }
                RoleOption(role == 2, isOwner, stringResource(R.string.roles_leader), stringResource(R.string.roles_leader_desc)) { role = 2 }
                if (!isOwner) {
                    Text(stringResource(R.string.roles_owner_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (role == 2 && isOwner) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = noEnd, onClick = { noEnd = true })
                        Text(stringResource(R.string.leaders_no_end), modifier = Modifier.padding(start = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !noEnd, onClick = { noEnd = false })
                        Text(stringResource(R.string.leaders_until_date), modifier = Modifier.padding(start = 4.dp))
                    }
                    if (!noEnd) {
                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = endMillis }
                                android.app.DatePickerDialog(
                                    context,
                                    { _, y, m, d -> endMillis = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }.timeInMillis },
                                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                                ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(shortDate(Date(endMillis))) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !(locked && role != 2 && row.isLeader), onClick = { onSave(role, if (role == 2 && !noEnd) Date(endMillis) else null) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
