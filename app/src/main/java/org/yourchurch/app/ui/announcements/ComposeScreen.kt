@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.announcements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.Announcement
import org.yourchurch.app.data.Category
import org.yourchurch.app.data.toLocalDate
import org.yourchurch.app.data.toStartOfDayDate

/** Admin-only form for posting or editing an announcement. */
@Composable
fun ComposeScreen(
    existing: Announcement?,
    authorUid: String,
    onSave: suspend (Announcement) -> Result<Unit>,
    onClose: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var message by rememberSaveable { mutableStateOf(existing?.message ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: Category.Service.id) }
    var dateIso by rememberSaveable {
        mutableStateOf((existing?.date?.toLocalDate() ?: LocalDate.now()).toString())
    }
    var pinned by rememberSaveable { mutableStateOf(existing?.pinned ?: false) }
    // An optional time of day (for example a prayer meeting at 18:00), like events have.
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasTime by rememberSaveable { mutableStateOf(existing?.time?.isNotBlank() == true) }
    var timeHour by rememberSaveable { mutableStateOf(existing?.time?.substringBefore(':')?.toIntOrNull() ?: 18) }
    var timeMinute by rememberSaveable { mutableStateOf(existing?.time?.substringAfter(':', "0")?.toIntOrNull() ?: 0) }
    // "Publish later": the announcement appears, and members are notified, at this day and time.
    var scheduled by rememberSaveable { mutableStateOf(existing?.publishAt != null) }
    var publishDateIso by rememberSaveable {
        mutableStateOf((existing?.publishAt?.toLocalDate() ?: LocalDate.now()).toString())
    }
    var publishHour by rememberSaveable {
        mutableStateOf(existing?.publishAt?.let { java.util.Calendar.getInstance().apply { time = it }.get(java.util.Calendar.HOUR_OF_DAY) } ?: 8)
    }
    var publishMinute by rememberSaveable {
        mutableStateOf(existing?.publishAt?.let { java.util.Calendar.getInstance().apply { time = it }.get(java.util.Calendar.MINUTE) } ?: 0)
    }
    var showPublishPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onClose)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (existing == null) R.string.new_announcement else R.string.edit_announcement),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
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
                value = title,
                onValueChange = { title = it.take(120) },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it.take(4000) },
                label = { Text(stringResource(R.string.field_message)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.field_type), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = category == c.id,
                        onClick = { category = c.id },
                        label = { Text(stringResource(c.labelRes)) }
                    )
                }
            }

            Text(stringResource(R.string.field_show_on), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    LocalDate.parse(dateIso)
                        .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault()))
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.announce_add_time), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = hasTime, onCheckedChange = { hasTime = it })
            }
            if (hasTime) {
                OutlinedButton(
                    onClick = {
                        android.app.TimePickerDialog(context, { _, h, m -> timeHour = h; timeMinute = m }, timeHour, timeMinute, true).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(String.format(Locale.US, "%02d:%02d", timeHour, timeMinute)) }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pin_to_top), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = pinned, onCheckedChange = { pinned = it })
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.publish_later), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = scheduled, onCheckedChange = { scheduled = it })
            }
            if (scheduled) {
                OutlinedButton(onClick = { showPublishPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        LocalDate.parse(publishDateIso)
                            .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault()))
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberDropdown(stringResource(R.string.publish_hour), publishHour, (0..23).toList()) { publishHour = it }
                    NumberDropdown(stringResource(R.string.publish_minute), publishMinute, listOf(0, 15, 30, 45)) { publishMinute = it }
                }
            }

            errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorRes = R.string.err_title_required
                        return@Button
                    }
                    val publishAt: Date? = if (scheduled) {
                        Date.from(
                            java.time.LocalDateTime.of(LocalDate.parse(publishDateIso), java.time.LocalTime.of(publishHour, publishMinute))
                                .atZone(java.time.ZoneId.systemDefault()).toInstant()
                        )
                    } else null
                    if (publishAt != null && publishAt.before(Date()) && existing?.publishAt?.time != publishAt.time) {
                        errorRes = R.string.err_publish_past
                        return@Button
                    }
                    errorRes = null
                    saving = true
                    val a = Announcement(
                        id = existing?.id ?: "",
                        title = title.trim(),
                        message = message.trim(),
                        category = category,
                        date = LocalDate.parse(dateIso).toStartOfDayDate(),
                        pinned = pinned,
                        createdAt = existing?.createdAt ?: Date(),
                        authorId = existing?.authorId?.ifEmpty { authorUid } ?: authorUid,
                        publishAt = publishAt,
                        time = if (hasTime) String.format(Locale.US, "%02d:%02d", timeHour, timeMinute) else ""
                    )
                    scope.launch {
                        val result = onSave(a)
                        saving = false
                        result.onSuccess { onClose() }.onFailure { errorRes = R.string.err_save_failed }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(if (existing == null) R.string.post_announcement else R.string.save_changes))
                }
            }
        }
    }

    if (showPublishPicker) {
        val publishState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(publishDateIso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPublishPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    publishState.selectedDateMillis?.let { ms ->
                        publishDateIso = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showPublishPicker = false
                }) { Text(stringResource(R.string.ok)) }
            }
        ) {
            DatePicker(state = publishState)
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(dateIso)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        dateIso = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** A small drop-down for picking a number, used for the hour and minute of a scheduled announcement. */
@Composable
private fun NumberDropdown(label: String, value: Int, choices: List<Int>, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { open = true }) { Text(label + " " + String.format(Locale.getDefault(), "%02d", value)) }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { n ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(String.format(Locale.getDefault(), "%02d", n)) },
                    onClick = { onChange(n); open = false }
                )
            }
        }
    }
}
