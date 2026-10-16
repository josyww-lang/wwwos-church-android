@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.community

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.Event
import org.yourchurch.app.data.EventsState
import org.yourchurch.app.data.EventsViewModel
import org.yourchurch.app.ui.chat.LinkifiedText
import org.yourchurch.app.ui.common.SkeletonCards

private data class EventTarget(val event: Event?)

@Composable
fun EventsPane(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    onFullScreen: (Boolean) -> Unit,
    vm: EventsViewModel = viewModel(key = "events-$uid")
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EventTarget?>(null) }
    var toDelete by remember { mutableStateOf<Event?>(null) }

    LaunchedEffect(selectedId, editing) { onFullScreen(selectedId != null || editing != null) }
    DisposableEffect(Unit) { onDispose { onFullScreen(false) } }
    LaunchedEffect(selectedId) { vm.watchRsvps(selectedId) }

    val editTarget = editing
    if (editTarget != null) {
        EventForm(existing = editTarget.event, uid = uid, vm = vm, onClose = { editing = null })
        return
    }

    val selected = state.events.firstOrNull { it.id == selectedId }
    if (selected != null) {
        EventDetail(
            event = selected,
            state = state,
            uid = uid,
            myName = myName,
            isAdmin = isAdmin,
            vm = vm,
            onBack = { selectedId = null },
            onEdit = { editing = EventTarget(selected) },
            onDelete = { toDelete = selected }
        )
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (isAdmin) {
                    ExtendedFloatingActionButton(
                        onClick = { editing = EventTarget(null) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(18.dp),
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.event_new), fontWeight = FontWeight.Medium) }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when {
                    state.loading -> SkeletonCards(Modifier.align(Alignment.TopCenter))
                    state.loadFailed -> Text(
                        stringResource(R.string.events_load_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                    state.events.isEmpty() -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(stringResource(R.string.events_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Text(
                            stringResource(if (isAdmin) R.string.events_empty_admin else R.string.events_empty_member),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.events, key = { it.id }) { event ->
                            EventCard(event = event, onClick = { selectedId = event.id })
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.event_delete_title)) },
            text = { Text(event.title) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(event)
                    if (selectedId == event.id) selectedId = null
                    toDelete = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.keep)) } }
        )
    }
}

@Composable
private fun EventCard(event: Event, onClick: () -> Unit) {
    val date = event.start.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .width(60.dp)
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(event.start) +
                        (if (event.location.isNotBlank()) "  ·  ${event.location}" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EventDetail(
    event: Event,
    state: EventsState,
    uid: String,
    myName: String,
    isAdmin: Boolean,
    vm: EventsViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val mine = state.rsvps.firstOrNull { it.uid == uid }?.status
    val going = state.rsvps.filter { it.status == "going" }
    val maybe = state.rsvps.filter { it.status == "maybe" }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(event.title, style = MaterialTheme.typography.headlineSmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, Locale.getDefault()).format(event.start),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (event.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(event.location, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (event.description.isNotBlank()) {
                LinkifiedText(event.description, MaterialTheme.colorScheme.onSurface)
            }

            OutlinedButton(
                onClick = { addToCalendar(context, event) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.event_add_calendar))
            }

            // Will you come?
            Text(stringResource(R.string.event_rsvp_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "going" to R.string.event_going,
                    "maybe" to R.string.event_maybe,
                    "no" to R.string.event_not_going
                ).forEach { (status, label) ->
                    FilterChip(
                        selected = mine == status,
                        onClick = {
                            if (mine == status) vm.clearRsvp(event.id, uid)
                            else vm.setRsvp(event.id, uid, myName, status)
                        },
                        label = { Text(stringResource(label)) }
                    )
                }
            }

            Text(
                stringResource(R.string.event_going_count, going.size) + "   ·   " +
                    stringResource(R.string.event_maybe_count, maybe.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(stringResource(R.string.event_whos_going), style = MaterialTheme.typography.titleSmall)
            if (going.isEmpty() && maybe.isEmpty()) {
                Text(
                    stringResource(R.string.event_nobody),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                (going + maybe).sortedBy { it.name.lowercase() }.forEach { rsvp ->
                    Text(
                        rsvp.name.ifBlank { "?" } + if (rsvp.status == "maybe") "  (${stringResource(R.string.event_maybe)})" else "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Admin-only form for adding or editing an event. */
@Composable
private fun EventForm(existing: Event?, uid: String, vm: EventsViewModel, onClose: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = existing?.start?.toInstant()?.atZone(zone)?.toLocalDateTime()
        ?: LocalDate.now().plusDays(1).atTime(18, 0)

    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var location by rememberSaveable { mutableStateOf(existing?.location ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var dateIso by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var hour by rememberSaveable { mutableStateOf(initial.hour) }
    var minute by rememberSaveable { mutableStateOf(initial.minute) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = !saving, onBack = onClose)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (existing == null) R.string.event_new else R.string.event_edit),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !saving) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(120) },
                label = { Text(stringResource(R.string.event_field_title)) },
                singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.event_field_date), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDate = true }, enabled = !saving, modifier = Modifier.weight(2f)) {
                    Text(
                        LocalDate.parse(dateIso)
                            .format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()))
                    )
                }
                OutlinedButton(onClick = { showTime = true }, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Text(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
                }
            }
            OutlinedTextField(
                value = location, onValueChange = { location = it.take(120) },
                label = { Text(stringResource(R.string.event_field_location)) },
                singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it.take(2000) },
                label = { Text(stringResource(R.string.event_field_description)) },
                minLines = 3, enabled = !saving, modifier = Modifier.fillMaxWidth()
            )

            errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorRes = R.string.err_title_required
                        return@Button
                    }
                    errorRes = null
                    saving = true
                    val start = LocalDate.parse(dateIso).atTime(hour, minute).atZone(zone).toInstant()
                    val event = Event(
                        id = existing?.id ?: "",
                        title = title.trim(),
                        description = description.trim(),
                        location = location.trim(),
                        start = Date.from(start),
                        createdBy = existing?.createdBy?.ifEmpty { uid } ?: uid,
                        createdAt = existing?.createdAt ?: Date()
                    )
                    scope.launch {
                        val result = vm.save(event)
                        saving = false
                        result.onSuccess { onClose() }.onFailure { errorRes = R.string.err_save_failed }
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.event_save), fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(dateIso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        dateIso = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = pickerState) }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = timeState) }
        )
    }
}
