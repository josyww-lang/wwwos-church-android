package org.yourchurch.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.yourchurch.app.R
import org.yourchurch.app.data.ChatMessage
import org.yourchurch.app.data.ScheduledMessage
import org.yourchurch.app.data.snippetOf
import org.yourchurch.app.ui.common.InfoHint

/** Make a poll: a question and two to six options. */
@Composable
fun PollDialog(onSend: (String, List<String>) -> Unit, onDismiss: () -> Unit) {
    var question by rememberSaveable { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    val clean = options.map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_poll)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it.take(200) },
                    label = { Text(stringResource(R.string.chat_poll_question)) },
                    modifier = Modifier.fillMaxWidth()
                )
                options.forEachIndexed { i, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { v -> options = options.toMutableList().also { it[i] = v.take(60) } },
                        singleLine = true,
                        label = { Text(stringResource(R.string.chat_poll_option, i + 1)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (options.size < 6) {
                    TextButton(onClick = { options = options + "" }) { Text(stringResource(R.string.chat_poll_add_option)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = question.isNotBlank() && clean.size >= 2, onClick = { onSend(question.trim(), clean) }) {
                Text(stringResource(R.string.chat_poll_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** The options of a poll with how many chose each. Tap an option to vote; tap it again to take the vote back. */
@Composable
fun PollView(message: ChatMessage, myUid: String, color: Color, onVote: (Int) -> Unit) {
    val total = message.votes.size
    val counts = IntArray(message.pollOptions.size)
    message.votes.values.forEach { if (it in counts.indices) counts[it]++ }
    val mine = message.votes[myUid]
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        message.pollOptions.forEachIndexed { i, option ->
            val fraction = if (total == 0) 0f else counts[i].toFloat() / total
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.10f))
                    .clickable { onVote(i) }
            ) {
                Box(Modifier.matchParentSize()) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color.copy(alpha = 0.22f)))
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (mine == i) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(option, color = color, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(counts[i].toString(), color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(stringResource(R.string.chat_poll_votes, total), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
    }
}

/** Search the messages of this chat that are loaded on the phone. Tap a result to jump to it. */
@Composable
fun ChatSearchDialog(messages: List<ChatMessage>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val results = remember(q, messages) {
        if (q.length < 2) emptyList()
        else messages.asReversed().filter { !it.deleted && it.text.contains(q, ignoreCase = true) }.take(100)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_search)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.chat_search_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (q.length >= 2 && results.isEmpty()) {
                    Text(stringResource(R.string.chat_search_none), style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results, key = { it.id }) { m ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(m.id) }.padding(vertical = 2.dp)) {
                            val time = m.createdAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(it) }.orEmpty()
                            Text(m.senderName + "  \u00B7  " + time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(snippetOf(m.text, q), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                InfoHint(stringResource(R.string.chat_search_note))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

/** Choose a date and a time to send the message. */
@Composable
fun ScheduleDialog(onSchedule: (Date) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var millis by remember {
        mutableLongStateOf(
            Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        )
    }
    var invalid by remember { mutableStateOf(false) }
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_schedule_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoHint(stringResource(R.string.chat_schedule_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = {
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                millis = Calendar.getInstance().apply { timeInMillis = millis; set(y, m, d) }.timeInMillis
                                invalid = false
                            },
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(Date(millis))) }
                OutlinedButton(
                    onClick = {
                        android.app.TimePickerDialog(
                            context,
                            { _, h, min ->
                                millis = Calendar.getInstance().apply { timeInMillis = millis; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0) }.timeInMillis
                                invalid = false
                            },
                            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(millis))) }
                if (invalid) Text(stringResource(R.string.chat_schedule_past), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (millis <= System.currentTimeMillis() + 60_000L) invalid = true else onSchedule(Date(millis)) }) {
                Text(stringResource(R.string.chat_schedule_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** The messages waiting to be sent in this chat, with a way to cancel each. */
@Composable
fun ScheduledListDialog(items: List<ScheduledMessage>, onCancel: (ScheduledMessage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_scheduled_list)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (items.isEmpty()) Text(stringResource(R.string.chat_scheduled_empty))
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(item.sendAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onCancel(item) }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cancel), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
