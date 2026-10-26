package org.yourchurch.app.ui.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.PlanPerson
import org.yourchurch.app.data.PlansViewModel
import org.yourchurch.app.data.ReadingPlan
import org.yourchurch.app.data.daysSince
import org.yourchurch.app.data.nextDayOf
import org.yourchurch.app.data.readingStreak
import org.yourchurch.app.data.todayIndex
import org.yourchurch.app.ui.common.InfoHint
import org.yourchurch.app.ui.common.SkeletonCards

/**
 * Plans: the Bible reading plan everyone follows. Everyone ticks off the days they have read and sees how far they
 * have got; leaders create the plan (one reading for each day) and see how everyone is doing.
 */
@Composable
fun PlansPane(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    canAdd: Boolean,
    onRead: (String, Int) -> Unit = { _, _ -> },
    onReflect: (String, Int) -> Unit = { _, _ -> },
    vm: PlansViewModel = viewModel(key = "plans-$uid")
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(uid, isAdmin, myName) { vm.start(uid, myName, isAdmin) }
    var showForm by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }       // all the days, or only the next few
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var heroMenu by remember { mutableStateOf(false) }
    val plan = state.plan

    if (state.loading) {
        SkeletonCards()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Leaders: "New reading plan" is at the top, where it is easy to find.
            if (canAdd) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { showForm = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.plans_new))
                        }
                    }
                }
            }
            if (plan == null) {
                item {
                    Text(
                        stringResource(if (canAdd) R.string.plans_empty_admin else R.string.plans_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                // Self-paced: the next reading is the first day this person has not read, so a plan waits for them if they stop.
                val next = nextDayOf(state.done, state.from, plan.days.size)
                val finished = next >= plan.days.size
                val away = !finished && state.done.isNotEmpty() && (state.lastReadAt?.let { daysSince(it) >= 3 } == true)
                val churchDay = plan.todayIndex()
                val canManage = isAdmin || (plan.authorId.isNotEmpty() && plan.authorId == uid)
                // 1. Your next reading, with what to do about it: read it, write about it, or mark it as read.
                item {
                    val onColor = MaterialTheme.colorScheme.onPrimary
                    val shape = RoundedCornerShape(22.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, shape)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                plan.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = onColor.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showInfo = !showInfo }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.plans_info), tint = onColor.copy(alpha = 0.85f))
                            }
                            Box {
                                IconButton(onClick = { heroMenu = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = onColor.copy(alpha = 0.85f))
                                }
                                DropdownMenu(expanded = heroMenu, onDismissRequest = { heroMenu = false }) {
                                    if (state.done.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.plans_start_over)) },
                                            onClick = {
                                                heroMenu = false
                                                confirmRestart = true
                                            }
                                        )
                                    }
                                    if (canManage) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.word_delete), color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                heroMenu = false
                                                confirmDelete = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        AnimatedVisibility(visible = showInfo) {
                            Text(
                                stringResource(R.string.plans_self_paced_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = onColor.copy(alpha = 0.9f),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        if (finished) {
                            Text(
                                stringResource(R.string.plans_finished),
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                                color = onColor
                            )
                        } else {
                            Text(
                                (if (away) stringResource(R.string.reading_welcome_back) + "  \u00B7  " else "") + stringResource(R.string.plans_day, next + 1).uppercase() + "  \u00B7  " + plannedDate(plan, next),
                                style = MaterialTheme.typography.labelMedium,
                                color = onColor.copy(alpha = 0.85f)
                            )
                            Text(
                                plan.days[next],
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                                color = onColor,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (plan.days.isEmpty()) 0f else (state.done.size.toFloat() / plan.days.size).coerceIn(0f, 1f) },
                            color = onColor,
                            trackColor = onColor.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        val streak = readingStreak(state.activeDays)
                        Text(
                            stringResource(R.string.reading_progress_pct, state.done.size, plan.days.size, if (plan.days.isEmpty()) 0 else state.done.size * 100 / plan.days.size) +
                                (if (streak > 1) "  \u00B7  " + stringResource(R.string.reading_streak, streak) else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = onColor.copy(alpha = 0.9f)
                        )
                        if (!finished) {
                            Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onRead(plan.days[next], next) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = onColor, contentColor = MaterialTheme.colorScheme.primary)
                                ) { Text(stringResource(R.string.plans_read_it)) }
                                OutlinedButton(
                                    onClick = { onReflect(plan.days[next], next) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = onColor),
                                    border = BorderStroke(1.dp, onColor.copy(alpha = 0.6f))
                                ) {
                                    if (next in state.reflected) {
                                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(stringResource(R.string.plans_reflect))
                                }
                            }
                            TextButton(
                                onClick = { vm.toggleDay(next) },
                                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = onColor)
                            ) { Text(stringResource(R.string.reading_mark)) }
                        }
                    }
                }
                // 2. Where the church is, in small letters.
                if (churchDay in 0 until plan.days.size) {
                    item {
                        Text(
                            stringResource(R.string.plans_church_day, churchDay + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                // 3. The days: the next few, or all of them, or the ones that match a search. One short line for each day, with the
                // day the church has planned it for.
                val searching = query.isNotBlank()
                val shownDays =
                    if (searching) plan.days.indices.filter { plan.days[it].contains(query.trim(), ignoreCase = true) || (it + 1).toString() == query.trim() }
                    else if (showAll) plan.days.indices.toList()
                    else ((next + 1)..(next + 3)).filter { it < plan.days.size }
                item {
                    if (searchOpen) {
                        SearchField(query = query, onQuery = { query = it }, onClose = { searchOpen = false })
                    } else {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(if (showAll) R.string.plans_all_days else R.string.plans_coming_up),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            SearchIconButton(onClick = { searchOpen = true })
                        }
                    }
                }
                if (searching && shownDays.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.search_none, query.trim()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                items(shownDays, key = { "d$it" }) { index ->
                    PlanDayRow(
                        index = index,
                        reading = plan.days[index],
                        date = plannedDate(plan, index),
                        isNext = index == next && !finished,
                        done = index in state.done,
                        reflected = index in state.reflected,
                        onToggle = { vm.toggleDay(index) },
                        onRead = { onRead(plan.days[index], index) },
                        onReflect = { onReflect(plan.days[index], index) },
                        onContinue = { vm.continueFrom(index) }
                    )
                }
                if (!searching && plan.days.size > 4) {
                    item {
                        TextButton(onClick = { showAll = !showAll }) {
                            Text(stringResource(if (showAll) R.string.plans_show_less else R.string.plans_show_all) + "  (" + plan.days.size + ")")
                        }
                    }
                }
                // 4. For leaders: how everyone is doing.
                if (isAdmin && state.everyone.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.plans_for_leaders),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }
                    item { EveryoneCard(plan, state.everyone) }
                }
            }
        }
    }

    if (showForm) {
        PlanForm(
            onSave = { title, start, days ->
                scope.launch { vm.createPlan(title, start, days, uid) }
                showForm = false
            },
            onDismiss = { showForm = false }
        )
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.plans_start_over)) },
            text = { Text(stringResource(R.string.plans_start_over_confirm)) },
            confirmButton = { TextButton(onClick = { confirmRestart = false; vm.restart() }) { Text(stringResource(R.string.plans_start_over)) } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (confirmDelete && plan != null) {
        ConfirmDelete(
            message = R.string.plans_delete_confirm,
            onConfirm = { vm.deletePlan(plan); confirmDelete = false },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** For leaders: how far each person has got. */
@Composable
private fun EveryoneCard(plan: ReadingPlan, people: List<PlanPerson>) {
    // Someone is "quiet" when they have not finished and have not read for a week.
    fun quiet(person: PlanPerson): Boolean =
        person.done < plan.days.size && (person.lastActive?.let { daysSince(it) >= 7 } ?: true)
    WordCard(onClick = {}) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.plans_everyone), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp))
            if (people.any { quiet(it) }) {
                InfoHint(stringResource(R.string.plans_quiet_hint))
            }
            people.forEach { person ->
                val percent = if (plan.days.isEmpty()) 0 else person.done * 100 / plan.days.size
                val since = person.lastActive?.let { daysSince(it) }
                Column {
                    Row {
                        Text(person.name.ifBlank { "?" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${person.done} / ${plan.days.size}  \u00B7  $percent%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(
                        progress = { if (plan.days.isEmpty()) 0f else (person.done.toFloat() / plan.days.size).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                        trackColor = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, 0.12f)
                    )
                    val lastText = when {
                        since == null -> ""
                        since <= 0 -> stringResource(R.string.reading_read_today)
                        else -> stringResource(R.string.plans_last_active_days, since)
                    }
                    val reflectedText = if (person.reflected > 0) stringResource(R.string.plans_reflected_n, person.reflected) else ""
                    val line = listOf(lastText, reflectedText).filter { it.isNotBlank() }.joinToString("  \u00B7  ")
                    if (line.isNotBlank()) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (quiet(person)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Create a plan: a name, a start date, and one reading for each day (one per line). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanForm(onSave: (String, Date, List<String>) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var readings by rememberSaveable { mutableStateOf("") }
    var startDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var showPicker by remember { mutableStateOf(false) }
    val days = readings.lines().map { it.trim().take(60) }.filter { it.isNotEmpty() }.take(400)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                    Text(stringResource(R.string.plans_new), style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = title.isNotBlank() && days.isNotEmpty(),
                        onClick = {
                            val start = Date.from(LocalDate.ofEpochDay(startDay).atStartOfDay(ZoneId.systemDefault()).toInstant())
                            onSave(title.trim(), start, days)
                        }
                    ) { Text(stringResource(R.string.save)) }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it.take(80) }, singleLine = true,
                        label = { Text(stringResource(R.string.plans_title)) }, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.plans_start) + ": " +
                                LocalDate.ofEpochDay(startDay).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault()))
                        )
                    }
                    OutlinedTextField(
                        value = readings, onValueChange = { readings = it }, minLines = 10,
                        label = { Text(stringResource(R.string.plans_readings)) }, modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.plans_readings_count, days.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { startDay = it / 86_400_000L }
                    showPicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = pickerState) }
    }
}

/** One day of the plan, in one short line: tick it, tap it to read it; the three dots offer Read, Reflect and Continue from here. */
@Composable
private fun PlanDayRow(
    index: Int,
    reading: String,
    date: String,
    isNext: Boolean,
    done: Boolean,
    reflected: Boolean,
    onToggle: () -> Unit,
    onRead: () -> Unit,
    onReflect: () -> Unit,
    onContinue: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isNext) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onRead)
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = done, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.plans_day, index + 1) + "  \u00B7  " + date,
                style = MaterialTheme.typography.labelMedium,
                color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(reading, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.plans_read_it)) }, onClick = { menu = false; onRead() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plans_reflect) + if (reflected) "  \u2713" else "") },
                    onClick = { menu = false; onReflect() }
                )
                if (!done) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.plans_continue_here)) }, onClick = { menu = false; onContinue() })
                }
            }
        }
    }
}

/** The day the church has planned a day of the plan for, for example "Thu 23 Oct" (the plan starts on its start date). */
private fun plannedDate(plan: ReadingPlan, index: Int): String {
    val day = java.util.Calendar.getInstance().apply {
        time = plan.startDate
        add(java.util.Calendar.DAY_OF_YEAR, index)
    }
    return java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(day.time)
}
