@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.community

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.ServingSlot
import org.yourchurch.app.data.ServingViewModel
import org.yourchurch.app.data.toLocalDate
import org.yourchurch.app.ui.common.SkeletonCards

private data class SlotTarget(val slot: ServingSlot?)

/** The serving rota: jobs people can sign up for. Admins add and edit them. */
@Composable
fun ServingPane(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    onFullScreen: (Boolean) -> Unit,
    vm: ServingViewModel = viewModel(key = "serving-$uid")
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SlotTarget?>(null) }
    var toDelete by remember { mutableStateOf<ServingSlot?>(null) }
    val failedText = stringResource(R.string.err_save_failed)

    LaunchedEffect(editing) { onFullScreen(editing != null) }
    DisposableEffect(Unit) { onDispose { onFullScreen(false) } }
    BackHandler(enabled = editing != null) { editing = null }
    LaunchedEffect(state.actionFailed) {
        if (state.actionFailed) {
            Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
            vm.clearActionFailed()
        }
    }

    val target = editing
    if (target != null) {
        SlotForm(existing = target.slot, onSave = { vm.save(it, uid) }, onClose = { editing = null })
        return
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> SkeletonCards(Modifier.align(Alignment.TopCenter))
            state.slots.isEmpty() -> Text(
                stringResource(if (isAdmin) R.string.serve_empty_admin else R.string.serve_empty_member),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(state.slots, key = { _, s -> s.id }) { index, slot ->
                    val previous = state.slots.getOrNull(index - 1)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (previous == null || previous.start.toLocalDate() != slot.start.toLocalDate()) {
                            Text(
                                slot.start.toLocalDate()
                                    .format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        SlotCard(
                            slot = slot,
                            uid = uid,
                            isAdmin = isAdmin,
                            onToggle = { vm.toggleSignUp(slot, uid, myName) },
                            onEdit = { editing = SlotTarget(slot) },
                            onDelete = { toDelete = slot }
                        )
                    }
                }
            }
        }
        if (isAdmin) {
            ExtendedFloatingActionButton(
                onClick = { editing = SlotTarget(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_label), fontWeight = FontWeight.Medium) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            )
        }
    }

    toDelete?.let { slot ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.serve_delete_title)) },
            text = { Text("${slot.title}\n\n${stringResource(R.string.serve_delete_text)}") },
            confirmButton = {
                TextButton(onClick = { vm.delete(slot); toDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.keep)) } }
        )
    }
}

@Composable
private fun SlotCard(
    slot: ServingSlot,
    uid: String,
    isAdmin: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val mine = uid in slot.volunteers
    var menuOpen by remember { mutableStateOf(false) }
    val time = slot.start.toInstant().atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(slot.title, style = MaterialTheme.typography.titleLarge)
                    Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isAdmin) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menuOpen = false; onEdit() })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
            }
            if (slot.notes.isNotBlank()) Text(slot.notes, style = MaterialTheme.typography.bodyMedium)

            LinearProgressIndicator(
                progress = { (slot.volunteers.size.toFloat() / slot.needed.coerceAtLeast(1)).coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.serve_needed, slot.volunteers.size, slot.needed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (slot.volunteerNames.isEmpty()) stringResource(R.string.serve_nobody)
                else slot.volunteerNames.values.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium
            )

            if (mine) {
                OutlinedButton(onClick = onToggle, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.serve_withdraw))
                }
            } else {
                Button(
                    onClick = onToggle,
                    enabled = !slot.full,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(if (slot.full) R.string.serve_full else R.string.serve_sign_up)) }
            }
        }
    }
}

@Composable
private fun SlotForm(existing: ServingSlot?, onSave: suspend (ServingSlot) -> Result<Unit>, onClose: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val startLocal = existing?.start?.toInstant()?.atZone(zone)
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var needed by rememberSaveable { mutableStateOf(existing?.needed ?: 2) }
    var dateIso by rememberSaveable { mutableStateOf((startLocal?.toLocalDate() ?: LocalDate.now()).toString()) }
    var timeText by rememberSaveable { mutableStateOf((startLocal?.toLocalTime() ?: LocalTime.of(10, 30)).withSecond(0).withNano(0).toString()) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val date = LocalDate.parse(dateIso)
    val time = LocalTime.parse(timeText)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (existing == null) R.string.serve_new else R.string.serve_edit),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(80) },
                label = { Text(stringResource(R.string.serve_field_title)) },
                singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showDate = true }, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())))
                }
                OutlinedButton(onClick = { showTime = true }, enabled = !saving, modifier = Modifier.weight(0.6f)) {
                    Text(time.format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.serve_people_needed), style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { needed = (needed - 1).coerceAtLeast(1) }, enabled = !saving) {
                        Icon(Icons.Filled.Remove, contentDescription = null)
                    }
                    Text("$needed", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { needed = (needed + 1).coerceAtMost(50) }, enabled = !saving) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }
            }
            OutlinedTextField(
                value = notes, onValueChange = { notes = it.take(300) },
                label = { Text(stringResource(R.string.serve_field_notes)) },
                minLines = 2, enabled = !saving, modifier = Modifier.fillMaxWidth()
            )
            if (failed) Text(stringResource(R.string.err_save_failed), color = MaterialTheme.colorScheme.error)

            Button(
                onClick = {
                    if (title.isBlank()) { failed = true; return@Button }
                    failed = false
                    saving = true
                    val start = Date.from(LocalDateTime.of(date, time).atZone(zone).toInstant())
                    scope.launch {
                        val result = onSave(
                            ServingSlot(
                                id = existing?.id ?: "",
                                title = title.trim(),
                                notes = notes.trim(),
                                start = start,
                                needed = needed,
                                volunteers = existing?.volunteers ?: emptyList(),
                                volunteerNames = existing?.volunteerNames ?: emptyMap(),
                                createdBy = existing?.createdBy ?: ""
                            )
                        )
                        saving = false
                        result.onSuccess { onClose() }.onFailure { failed = true }
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.save), fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        dateIso = java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = pickerState) }
    }
    if (showTime) {
        val timeState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    timeText = LocalTime.of(timeState.hour, timeState.minute).toString()
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = timeState) }
        )
    }
}
