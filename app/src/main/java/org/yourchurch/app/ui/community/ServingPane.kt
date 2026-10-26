@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.community

import org.yourchurch.app.ui.common.FilterOption
import org.yourchurch.app.ui.common.FilterDropdown
import org.yourchurch.app.ui.common.SegmentedChoice
import org.yourchurch.app.ui.common.InfoHint
import org.yourchurch.app.data.doubleDuties
import org.yourchurch.app.data.NameDirectory
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import org.yourchurch.app.ui.common.ChipFlow
import org.yourchurch.app.data.ServingMember
import org.yourchurch.app.data.SERVE_SIGNUP
import org.yourchurch.app.data.SERVE_ASSIGNED
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
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

/**
 * The serving rota. "My schedule" lists what a person serves (assigned by the leaders, or signed up for), with a button to put it in
 * their calendar. Leaders see everything, plan schedules for several weeks (only the people they choose are told), and can see who
 * cannot come. Open duties can be signed up for.
 */
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
    LaunchedEffect(uid, isAdmin) { vm.start(uid, isAdmin) }
    LaunchedEffect(Unit) { NameDirectory.start() }
    var editing by remember { mutableStateOf<SlotTarget?>(null) }
    var planning by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ServingSlot?>(null) }
    var showAllDates by rememberSaveable { mutableStateOf(false) }         // leaders: the next dates first, the rest on request
    var deleteAll by remember { mutableStateOf(false) }                  // delete all the duties shown (one kind of duty, or all)
    // Says what happened after a delete: how many were deleted, or the reason it did not work.
    val tellDeleted: (Result<Unit>, Int) -> Unit = { result, count ->
        val text = result.fold(
            onSuccess = { context.getString(R.string.serve_deleted, count) },
            onFailure = { e ->
                val reason = when {
                    e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> context.getString(R.string.serve_err_permission)
                    e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> context.getString(R.string.serve_err_network)
                    e is kotlinx.coroutines.TimeoutCancellationException -> context.getString(R.string.serve_err_network)
                    else -> e.message.orEmpty().take(80)
                }
                context.getString(R.string.serve_delete_failed, reason)
            }
        )
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
    var cantFor by remember { mutableStateOf<ServingSlot?>(null) }       // "I can't make it": the dialog for this duty
    var newMenu by remember { mutableStateOf(false) }
    var dayFor by remember { mutableStateOf<Pair<LocalDate, String?>?>(null) }          // "cancel or push this day": the dialog for this day
    val scope = rememberCoroutineScope()
    // 0: my schedule. 1: everything (leaders) or the open duties (everyone else).
    var view by rememberSaveable { mutableStateOf(if (isAdmin) 1 else 0) }

    var duty by rememberSaveable { mutableStateOf("") }
    val failedText = stringResource(R.string.err_save_failed)
    val noCalendar = stringResource(R.string.serve_calendar_failed)

    LaunchedEffect(editing, planning) { onFullScreen(editing != null || planning) }
    DisposableEffect(Unit) { onDispose { onFullScreen(false) } }
    BackHandler(enabled = editing != null || planning) {
        editing = null
        planning = false
    }
    LaunchedEffect(state.actionFailed) {
        if (state.actionFailed) {
            Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
            vm.clearActionFailed()
        }
    }

    if (planning) {
        ScheduleBuilder(myUid = uid, myName = myName, vm = vm, onClose = { planning = false })
        return
    }
    val target = editing
    if (target != null) {
        SlotForm(
            existing = target.slot,
            vm = vm,
            me = ServingMember(uid, myName.ifBlank { "?" }),
            onSave = { vm.save(it, uid) },
            onClose = { editing = null }
        )
        return
    }

    val mine = state.slots.filter { uid in it.volunteers }
    val shown = when {
        view == 0 -> mine
        isAdmin -> state.slots.filter { duty.isEmpty() || it.title.equals(duty, ignoreCase = true) }
        else -> state.slots.filter { !it.assigned && it.open && !it.cancelled }
    }
    // A long schedule starts with the next 6 dates (the rest are one tap away), so the page is short and quick.
    val dateLimit = 6
    val allDates = shown.map { it.start.toLocalDate() }.distinct()
    val limited = isAdmin && view == 1 && !showAllDates && allDates.size > dateLimit
    val visible = if (limited) { val keep = allDates.take(dateLimit).toSet(); shown.filter { it.start.toLocalDate() in keep } } else shown
    // The duties of this person that are still to come (not those they said they cannot do): these go in the calendar.
    val toCalendar = mine.filter { uid !in it.cantMake && !it.cancelled }
    // For the leaders: the people who have more than one duty on the same day (allowed; shown as a note under the date).
    val doublesByDate = remember(state.slots, NameDirectory.names.size) { if (isAdmin) doubleDuties(state.slots).groupBy { it.date } else emptyMap() }
    val duties = state.slots.map { it.title.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sorted()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // "My schedule" (what a leader assigned to me, and what I signed up for), and then: for a leader everything, for a member
            // only the duties open for sign-up. A member never sees duties that were given to other people.
            // ONE compact line: the view (My schedule / Everything, or Open to sign up) and, for a leader, a small menu to show
            // only one kind of duty. (Before: a row of chips for each, wrapping onto many lines.)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentedChoice(
                    options = listOf(stringResource(R.string.serve_tab_mine), stringResource(if (isAdmin) R.string.serve_tab_all else R.string.serve_tab_open)),
                    selected = view,
                    onSelect = { view = it },
                    modifier = Modifier.weight(1f)
                )
                if (isAdmin && view == 1 && duties.size > 1) {
                    FilterDropdown(
                        options = listOf(FilterOption("", stringResource(R.string.serve_all_duties))) +
                            duties.map { FilterOption(it, it, dutyColor(it, state.duties)) },
                        selectedKey = duties.firstOrNull { it.equals(duty, ignoreCase = true) } ?: "",
                        onSelect = { duty = it }
                    )
                }
            }
            // Leaders: all the duties of the chosen kind can be deleted at once (to plan them again).
            if (isAdmin && view == 1 && duty.isNotEmpty() && shown.isNotEmpty()) {
                TextButton(onClick = { deleteAll = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.serve_delete_all, shown.size, duty), color = MaterialTheme.colorScheme.error)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> SkeletonCards(Modifier.align(Alignment.TopCenter))
                    shown.isEmpty() -> Text(
                        stringResource(
                            when {
                                view == 0 -> R.string.serve_empty_mine
                                isAdmin -> R.string.serve_empty_admin
                                else -> R.string.serve_empty_member
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (view == 0 && toCalendar.isNotEmpty()) {
                            item(key = "calendar-banner") {
                                CalendarBanner(
                                    onAll = {
                                        if (!addAllServingToCalendar(context, toCalendar)) Toast.makeText(context, noCalendar, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                        itemsIndexed(visible, key = { _, s -> s.id }) { index, slot ->
                            val previous = visible.getOrNull(index - 1)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (previous == null || previous.start.toLocalDate() != slot.start.toLocalDate()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                        Text(
                                            slot.start.toLocalDate()
                                                .format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // Leaders: cancel this day, or push it (and the days after it) one or two weeks ahead.
                                        if (isAdmin && view == 1) {
                                            IconButton(onClick = { dayFor = slot.start.toLocalDate() to null }) {
                                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.serve_day_menu))
                                            }
                                        }
                                    }
                                    if (isAdmin && view == 1) {
                                        val doubleToday = doublesByDate[slot.start.toLocalDate()].orEmpty()
                                        if (doubleToday.isNotEmpty()) {
                                            InfoHint(doubleToday.joinToString("\n") { dd ->
                                                context.getString(R.string.serve_double_short, dd.name, dd.duties.size, dd.duties.joinToString(", "))
                                            })
                                        }
                                    }
                                }
                                SlotCard(
                                    slot = slot,
                                    accent = if (slot.cancelled) Color.Gray else dutyColor(slot.title, state.duties),
                                    uid = uid,
                                    isAdmin = isAdmin,
                                    // Someone who signed up and leaves is asked why (a comment, and maybe a date that suits them).
                                    onToggle = { if (uid in slot.volunteers) cantFor = slot else vm.toggleSignUp(slot, uid, myName) },
                                    onRestore = { scope.launch { vm.restore(slot, uid) } },
                                    onConfirm = { vm.confirm(slot, uid) },
                                    onDayActions = { dayFor = slot.start.toLocalDate() to slot.title },
                                    onCantMake = { if (uid in slot.cantMake) vm.undoCantMake(slot, uid) else cantFor = slot },
                                    onCalendar = {
                                        if (!addServingToCalendar(context, slot)) Toast.makeText(context, noCalendar, Toast.LENGTH_SHORT).show()
                                    },
                                    onEdit = { editing = SlotTarget(slot) },
                                    onDelete = { toDelete = slot }
                                )
                            }
                        }
                        if (limited) {
                            item(key = "show-all-dates") {
                                TextButton(onClick = { showAllDates = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.serve_show_all, allDates.size - dateLimit))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (isAdmin) {
            Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { newMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_label), fontWeight = FontWeight.Medium) }
                )
                DropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.serve_plan)) },
                        onClick = { newMenu = false; planning = true }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.serve_new_one)) },
                        onClick = { newMenu = false; editing = SlotTarget(null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.serve_delete_every_menu), color = MaterialTheme.colorScheme.error) },
                        onClick = { newMenu = false; view = 1; duty = ""; deleteAll = true }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.serve_share)) },
                        onClick = {
                            newMenu = false
                            shareServingScheduleText(context, shown, context.getString(R.string.serve_share_head), context.getString(R.string.serve_cancelled))
                        }
                    )
                }
            }
        }
    }

    cantFor?.let { slot ->
        CantMakeDialog(
            slot = slot,
            onSend = { note, suggest ->
                if (slot.assigned) vm.sayCantMake(slot, uid, myName, note, suggest) else vm.withdrawWithReason(slot, uid, myName, note, suggest)
                cantFor = null
            },
            onClose = { cantFor = null }
        )
    }

    if (deleteAll) {
        AlertDialog(
            onDismissRequest = { deleteAll = false },
            title = { Text(if (duty.isEmpty()) stringResource(R.string.serve_delete_every, shown.size) else stringResource(R.string.serve_delete_all, shown.size, duty)) },
            text = { Text(stringResource(R.string.serve_delete_all_text)) },
            confirmButton = {
                TextButton(onClick = {
                    val gone = shown
                    deleteAll = false
                    duty = ""
                    scope.launch { tellDeleted(vm.deleteMany(gone, uid), gone.size) }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteAll = false }) { Text(stringResource(R.string.keep)) } }
        )
    }

    dayFor?.let { (day, firstDuty) ->
        DayActionsDialog(
            day = day,
            duties = state.slots.map { it.title.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sorted(),
            firstDuty = firstDuty,
            onApply = { action, weeks, andAfter, chosenDuties ->
                scope.launch {
                    if (action == "cancel") vm.cancelDay(day, uid, chosenDuties) else vm.pushDays(day, weeks, andAfter, uid, chosenDuties)
                }
                dayFor = null
            },
            onClose = { dayFor = null }
        )
    }

    toDelete?.let { slot ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.serve_delete_title)) },
            text = { Text("${slot.title}\n\n${stringResource(R.string.serve_delete_text)}") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch { tellDeleted(vm.deleteOne(slot, uid), 1) }
                }) {
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
    accent: Color,
    uid: String,
    isAdmin: Boolean,
    onToggle: () -> Unit,
    onRestore: () -> Unit,
    onConfirm: () -> Unit,
    onDayActions: () -> Unit,
    onCantMake: () -> Unit,
    onCalendar: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val mine = uid in slot.volunteers
    var menuOpen by remember { mutableStateOf(false) }
    val time = slot.start.toInstant().atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    // Who cannot come, with their comment and the date that suits them. A leader sees everyone's; a person sees only their own.
    val dayFormat = remember { java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val suggestsLabel = stringResource(R.string.serve_suggests)
    val cantIds = (slot.cantMake + slot.cantInfo.keys.filter { it !in slot.volunteers }).distinct()       // those who cannot come, and those who left
    val cantLines = cantIds.filter { isAdmin || it == uid }.mapNotNull { id ->
        val name = NameDirectory.nameOf(id, slot.volunteerNames[id] ?: slot.cantInfo[id]?.name.orEmpty()).ifBlank { return@mapNotNull null }
        val info = slot.cantInfo[id]
        buildString {
            append(name)
            if (info != null && info.note.isNotBlank()) append(": ").append(info.note)
            info?.suggest?.let { append("  \u00B7  ").append(suggestsLabel).append(" ").append(dayFormat.format(it)) }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().alpha(if (slot.cancelled) 0.6f else 1f)
    ) {
        Column(
            Modifier
                .drawBehind { drawRect(accent, size = Size(7.dp.toPx(), size.height)) }      // a stripe in the colour of this duty
                .padding(start = 25.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                            DropdownMenuItem(text = { Text(stringResource(R.string.serve_day_menu)) }, onClick = { menuOpen = false; onDayActions() })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
            }
            if (slot.notes.isNotBlank()) Text(slot.notes, style = MaterialTheme.typography.bodyMedium)
            // Who cannot come (or left), and why: for the leaders, and each person for themselves.
            if (cantLines.isNotEmpty() && !slot.cancelled) {
                Text(
                    stringResource(R.string.serve_cant_list, cantLines.joinToString("\n")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // For the leaders: who has said "I'll be there".
            if (isAdmin && slot.assigned && !slot.cancelled && slot.confirmed.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.serve_confirmed_list,
                        slot.confirmed.joinToString(", ") { NameDirectory.nameOf(it, slot.volunteerNames[it].orEmpty()) }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (slot.cancelled) {
                // The program of that day was cancelled: nothing to do, nothing is reminded.
                Text(stringResource(R.string.serve_cancelled), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                if (slot.volunteerNames.isNotEmpty()) Text(slot.volunteerNames.entries.joinToString(", ") { NameDirectory.nameOf(it.key, it.value) }, style = MaterialTheme.typography.bodyMedium)
                if (isAdmin) {
                    OutlinedButton(onClick = onRestore, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.serve_restore))
                    }
                }
            } else if (slot.assigned) {
                // The leaders chose the people: it is a schedule, not a sign-up.
                Text(
                    if (slot.volunteerNames.isEmpty()) stringResource(R.string.serve_nobody) else slot.volunteerNames.entries.joinToString(", ") { NameDirectory.nameOf(it.key, it.value) },
                    style = MaterialTheme.typography.bodyLarge
                )
                if (mine) {
                    // Accept or refuse, in one line: the red number on the phone stays until the person does one of the two.
                    when {
                        uid in slot.cantMake -> OutlinedButton(onClick = onCantMake, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.serve_cant_make_undo))
                        }
                        uid in slot.confirmed -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "\u2713 " + stringResource(R.string.serve_confirmed_you),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onCantMake) { Text(stringResource(R.string.serve_cant_make)) }
                        }
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = onConfirm, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.serve_confirm))
                            }
                            OutlinedButton(onClick = onCantMake, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.serve_cant_make))
                            }
                        }
                    }
                }
            } else {
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
                    else slot.volunteerNames.entries.joinToString(", ") { NameDirectory.nameOf(it.key, it.value) },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (mine) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onCalendar, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.serve_add_calendar))
                        }
                        OutlinedButton(onClick = onToggle, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.serve_withdraw))
                        }
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
}

@Composable
private fun SlotForm(existing: ServingSlot?, vm: ServingViewModel, me: ServingMember, onSave: suspend (ServingSlot) -> Result<Unit>, onClose: () -> Unit) {
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
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadMembers() }
    // Who serves: people sign up themselves, or the leader chooses the people (and only they are told).
    // Who serves: people sign up themselves (the default for one duty), or the leader chooses the people.
    var assignMode by rememberSaveable { mutableStateOf(existing?.assigned == true) }
    // The people a leader chose for this duty (the type is written out, so Kotlin does not have to guess it).
    var chosen by remember {
        mutableStateOf<List<ServingMember>>(
            if (existing != null && existing.assigned) existing.volunteers.map { ServingMember(it, existing.volunteerNames[it] ?: "?") }
            else emptyList()
        )
    }
    var length by rememberSaveable { mutableStateOf(existing?.durationMin ?: 90) }
    var showPeople by remember { mutableStateOf(false) }
    val dutyNames = DUTY_NAMES.map { stringResource(it) }

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
            DutyChips(
                duty = title, custom = state.duties, enabled = !saving, onPick = { title = it },
                onAdd = { name ->
                    title = name
                    scope.launch { vm.addDuty(name, me.uid) }
                }
            )
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
            CountStepper(stringResource(R.string.serve_length), length, 15, 480, step = 15, unit = " min", enabled = !saving) { length = it }
            ChipFlow {
                FilterChip(selected = !assignMode, onClick = { assignMode = false }, label = { Text(stringResource(R.string.serve_mode_signup)) })
                FilterChip(selected = assignMode, onClick = { assignMode = true }, label = { Text(stringResource(R.string.serve_mode_assign)) })
            }
            if (assignMode) {
                Text(
                    if (chosen.isEmpty()) stringResource(R.string.serve_nobody) else chosen.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(onClick = { showPeople = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.serve_choose_people))
                }
            } else {
                CountStepper(stringResource(R.string.serve_people_needed), needed, 1, 50, enabled = !saving) { needed = it }
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
                                needed = if (assignMode) chosen.size.coerceAtLeast(1) else needed,
                                volunteers = if (assignMode) chosen.map { it.uid } else existing?.takeIf { !it.assigned }?.volunteers.orEmpty(),
                                volunteerNames = if (assignMode) chosen.associate { it.uid to it.name.take(60) }
                                else existing?.takeIf { !it.assigned }?.volunteerNames.orEmpty(),
                                createdBy = existing?.createdBy ?: "",
                                mode = if (assignMode) SERVE_ASSIGNED else SERVE_SIGNUP,
                                open = !assignMode,
                                durationMin = length,
                                cantMake = if (assignMode) existing?.cantMake.orEmpty().filter { id -> chosen.any { it.uid == id } } else emptyList(),
                                cantInfo = if (assignMode) existing?.cantInfo.orEmpty().filterKeys { id -> chosen.any { it.uid == id } } else existing?.cantInfo.orEmpty(),
                                cancelled = existing?.cancelled ?: false
                            )
                        )
                        saving = false
                        result.onSuccess {
                            onClose()
                        }.onFailure { failed = true }
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

    if (showPeople) {
        // What each person already has on that day (highlighted in the list), and their load. Allowed; only to see who is loaded.
        val sameDayMap = HashMap<String, MutableList<String>>()
        state.slots.filter {
            it.id != (existing?.id ?: "") && it.assigned && !it.cancelled && it.start.toLocalDate() == date && !it.title.equals(title.trim(), ignoreCase = true)
        }.forEach { sl -> sl.volunteers.forEach { u -> sameDayMap.getOrPut(u) { mutableListOf() }.add(sl.title.trim()) } }
        val loadMap = state.slots.filter { it.assigned && !it.cancelled && it.id != (existing?.id ?: "") }
            .flatMap { it.volunteers }.groupingBy { it }.eachCount()
        PeoplePickerDialog(
            members = (state.members + me + chosen).distinctBy { it.uid },
            loading = state.membersLoading,
            initial = chosen,
            max = 50,
            onDone = { chosen = it; showPeople = false },
            onClose = { showPeople = false },
            sameDay = sameDayMap,
            load = loadMap
        )
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

/** "I can't make it": a short comment for the leaders, and (if they wish) a date that suits the person better. */
@Composable
private fun CantMakeDialog(slot: ServingSlot, onSend: (String, Date?) -> Unit, onClose: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
    var note by remember { mutableStateOf("") }
    var suggestIso by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    val slotDate = slot.start.toLocalDate()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.serve_cant_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(slot.title + "  \u00B7  " + slotDate.format(dayFormat), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text(stringResource(R.string.serve_cant_why)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.serve_cant_suggest), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                        Text(suggestIso?.let { LocalDate.parse(it).format(dayFormat) } ?: stringResource(R.string.serve_cant_pick_date))
                    }
                    if (suggestIso != null) {
                        TextButton(onClick = { suggestIso = null }) { Text(stringResource(R.string.serve_cant_no_date)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // The date that suits them is kept at the same time of day as the duty.
                val suggest = suggestIso?.let {
                    Date.from(LocalDate.parse(it).atTime(slot.start.toInstant().atZone(zone).toLocalTime()).atZone(zone).toInstant())
                }
                onSend(note.trim(), suggest)
            }) { Text(stringResource(R.string.serve_cant_send)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }
    )
    if (showDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (suggestIso?.let { LocalDate.parse(it) } ?: slotDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        suggestIso = java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = pickerState) }
    }
}

/** On top of "My schedule": a gentle push to put the duties in the phone's calendar, where the phone reminds the person. */
@Composable
private fun CalendarBanner(onAll: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.serve_cal_banner_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            InfoHint(stringResource(R.string.serve_cal_banner_text))
            Button(onClick = onAll, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.serve_cal_all))
            }
        }
    }
}

/**
 * Cancel the program of a day, or push it (only that day, or that day and every later day) one or two weeks ahead, for all the duties
 * or only for the ones you choose (for example only Deaconia). The order of the schedule is kept.
 */
@Composable
private fun DayActionsDialog(
    day: LocalDate,
    duties: List<String>,
    firstDuty: String?,
    onApply: (action: String, weeks: Int, andAfter: Boolean, duties: Set<String>) -> Unit,
    onClose: () -> Unit
) {
    var action by remember { mutableStateOf("push") }
    var weeks by remember { mutableStateOf(1) }
    var andAfter by remember { mutableStateOf(true) }
    var chosen by remember { mutableStateOf(if (firstDuty != null) setOf(firstDuty) else emptySet<String>()) }
    val shortDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    val target = day.plusWeeks(weeks.toLong()).format(shortDay)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChipFlow {
                    FilterChip(selected = action == "push", onClick = { action = "push" }, label = { Text(stringResource(R.string.serve_day_push)) })
                    FilterChip(selected = action == "cancel", onClick = { action = "cancel" }, label = { Text(stringResource(R.string.serve_day_cancel)) })
                }
                if (action == "push") {
                    ChipFlow {
                        FilterChip(selected = weeks == 1, onClick = { weeks = 1 }, label = { Text(stringResource(R.string.serve_day_1w)) })
                        FilterChip(selected = weeks == 2, onClick = { weeks = 2 }, label = { Text(stringResource(R.string.serve_day_2w)) })
                    }
                    ChipFlow {
                        FilterChip(selected = andAfter, onClick = { andAfter = true }, label = { Text(stringResource(R.string.serve_day_after)) })
                        FilterChip(selected = !andAfter, onClick = { andAfter = false }, label = { Text(stringResource(R.string.serve_day_only)) })
                    }
                }
                // Which duties: all of them, or only the ones chosen here.
                Text(stringResource(R.string.serve_day_which), style = MaterialTheme.typography.labelLarge)
                ChipFlow {
                    FilterChip(selected = chosen.isEmpty(), onClick = { chosen = emptySet() }, label = { Text(stringResource(R.string.serve_all_duties)) })
                    duties.forEach { d ->
                        val on = chosen.any { it.equals(d, ignoreCase = true) }
                        FilterChip(
                            selected = on,
                            onClick = { chosen = if (on) chosen.filterNot { it.equals(d, ignoreCase = true) }.toSet() else chosen + d },
                            label = { Text(d) }
                        )
                    }
                }
                Text(
                    when {
                        action == "cancel" -> stringResource(R.string.serve_day_expl_cancel)
                        andAfter -> stringResource(R.string.serve_day_expl_after, target)
                        else -> stringResource(R.string.serve_day_expl_only, target)
                    } + if (chosen.isNotEmpty()) " " + stringResource(R.string.serve_day_expl_duties, chosen.joinToString(", ")) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(action, weeks, andAfter, chosen) }) { Text(stringResource(R.string.serve_day_apply)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }
    )
}
