package org.yourchurch.app.ui.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.FEEDBACK_CATEGORIES
import org.yourchurch.app.data.FeedbackMessage
import org.yourchurch.app.data.FeedbackViewModel

private fun categoryLabel(code: String): Int = when (code) {
    "idea" -> R.string.fb_idea
    "concern" -> R.string.fb_concern
    "praise" -> R.string.fb_praise
    else -> R.string.fb_other
}

/** Members send a private message to the leaders. Admins read them here. */
@Composable
fun FeedbackPane(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    vm: FeedbackViewModel = viewModel(key = "feedback-$uid")
) {
    LaunchedEffect(isAdmin) { vm.start(isAdmin) }
    if (isAdmin) FeedbackInbox(vm) else FeedbackForm(uid, myName, vm)
}

@Composable
private fun FeedbackForm(uid: String, myName: String, vm: FeedbackViewModel) {
    var category by rememberSaveable { mutableStateOf("idea") }
    var text by rememberSaveable { mutableStateOf("") }
    var anonymous by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.fb_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.fb_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FEEDBACK_CATEGORIES.forEach { code ->
                FilterChip(
                    selected = category == code,
                    onClick = { category = code },
                    label = { Text(stringResource(categoryLabel(code))) }
                )
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(2000); sent = false },
            label = { Text(stringResource(R.string.fb_message)) },
            minLines = 5,
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.fb_anonymous), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = anonymous, onCheckedChange = { anonymous = it }, enabled = !sending)
        }
        if (anonymous) {
            Text(
                stringResource(R.string.fb_anonymous_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (failed) Text(stringResource(R.string.err_save_failed), color = MaterialTheme.colorScheme.error)
        if (sent) Text(stringResource(R.string.fb_thanks), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)

        Button(
            onClick = {
                failed = false
                sending = true
                scope.launch {
                    vm.send(text.trim(), category, anonymous, uid, myName)
                        .onSuccess { sending = false; sent = true; text = "" }
                        .onFailure { sending = false; failed = true }
                }
            },
            enabled = text.isNotBlank() && !sending,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (sending) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.fb_send), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FeedbackInbox(vm: FeedbackViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val newCount = state.inbox.count { it.status != "done" }

    Column(Modifier.fillMaxSize()) {
        Text(
            "${stringResource(R.string.fb_inbox)} ($newCount)",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
        )
        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(20.dp))
            state.inbox.isEmpty() -> Text(
                stringResource(R.string.fb_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.inbox, key = { it.id }) { message -> InboxCard(message, vm) }
            }
        }
    }
}

@Composable
private fun InboxCard(m: FeedbackMessage, vm: FeedbackViewModel) {
    val done = m.status == "done"
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(categoryLabel(m.category)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(if (done) R.string.fb_handled else R.string.fb_new),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
            Text(
                stringResource(R.string.fb_from, if (m.anonymous || m.authorName.isBlank()) stringResource(R.string.fb_anonymous_label) else m.authorName) +
                    (m.createdAt?.let { "  ·  " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(it) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(m.text, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.toggleDone(m) }) {
                    Text(stringResource(if (done) R.string.fb_reopen else R.string.fb_mark_done))
                }
                TextButton(onClick = { vm.delete(m) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
