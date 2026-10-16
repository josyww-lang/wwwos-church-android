package org.yourchurch.app.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
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
import org.yourchurch.app.data.streakOf
import org.yourchurch.app.data.toLocalDate
import org.yourchurch.app.data.todayIndex
import org.yourchurch.app.ui.common.SkeletonCards

/**
 * Plans: the Bible reading plan everyone follows. Everyone ticks off the days they have read and sees how far they
 * have got; leaders create the plan (one reading for each day) and see how everyone is doing.
 */
@Composable
fun PlansPane(uid: String, myName: String, isAdmin: Boolean, canAdd: Boolean, vm: PlansViewModel = viewModel(key = "plans-$uid")) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(uid, isAdmin, myName) { vm.start(uid, myName, isAdmin) }
    var showForm by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val plan = state.plan

    if (state.loading) {
        SkeletonCards()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
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
                val today = plan.todayIndex()
                item {
                    WordCard(onClick = {}) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(plan.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), modifier = Modifier.weight(1f))
                                if (isAdmin || (plan.authorId.isNotEmpty() && plan.authorId == uid)) LeaderMenu(onEdit = null, onDelete = { confirmDelete = true })
                            }
                            Text(
                                stringResource(R.string.reading_progress, state.done.size, plan.days.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { if (plan.days.isEmpty()) 0f else (state.done.size.toFloat() / plan.days.size).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                            val streak = streakOf(state.done, today)
                            if (streak > 1) {
                                Text(stringResource(R.string.reading_streak, streak), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            if (today < 0) {
                                Text(
                                    stringResource(R.string.reading_starts, plan.startDate.toLocalDate().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                if (isAdmin && state.everyone.isNotEmpty()) {
                    item { EveryoneCard(plan, state.everyone) }
                }
                itemsIndexed(plan.days, key = { index, _ -> "d$index" }) { index, reading ->
                    val isToday = index == today
                    WordCard(onClick = { vm.toggleDay(index) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = index in state.done, onCheckedChange = { vm.toggleDay(index) })
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    stringResource(R.string.plans_day, index + 1) + "  \u00B7  " +
                                        plan.startDate.toLocalDate().plusDays(index.toLong())
                                            .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    reading,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (canAdd) {
                item {
                    OutlinedButton(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.plans_new))
                    }
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
    WordCard(onClick = {}) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.plans_everyone), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp))
            people.forEach { person ->
                Column {
                    Row {
                        Text(person.name.ifBlank { "?" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${person.done} / ${plan.days.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(
                        progress = { if (plan.days.isEmpty()) 0f else (person.done.toFloat() / plan.days.size).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                        trackColor = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, 0.12f)
                    )
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
