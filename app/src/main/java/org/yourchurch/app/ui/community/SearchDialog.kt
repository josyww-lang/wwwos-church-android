package org.yourchurch.app.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Locale
import org.yourchurch.app.R
import org.yourchurch.app.data.SearchItem
import org.yourchurch.app.data.SearchKind
import org.yourchurch.app.data.SearchViewModel
import org.yourchurch.app.data.snippetOf
import org.yourchurch.app.ui.theme.highlightColor
import org.yourchurch.app.ui.theme.highlightTextColor

/** Search across announcements, events, sermons and lessons. Tap a result to open its section. */
@Composable
fun SearchDialog(
    isAdmin: Boolean,
    onNavigate: (HomeTarget) -> Unit,
    onDismiss: () -> Unit,
    vm: SearchViewModel = viewModel(key = "search")
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(isAdmin) { vm.load(isAdmin) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val q = query.trim()
    val results = remember(q, state.items) {
        if (q.length < 2) emptyList() else state.items.filter {
            it.title.contains(q, ignoreCase = true) || it.extra.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = null) }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}),
                        modifier = Modifier.weight(1f).padding(end = 12.dp).focusRequester(focus)
                    )
                }

                when {
                    state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                    state.failed -> Text(stringResource(R.string.search_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
                    q.length < 2 -> Text(
                        stringResource(R.string.search_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                    results.isEmpty() -> Text(
                        stringResource(R.string.search_none, q),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SearchKind.entries.forEach { kind ->
                            val group = results.filter { it.kind == kind }.take(8)
                            if (group.isNotEmpty()) {
                                item(key = "h-$kind") {
                                    Text(
                                        stringResource(kindLabel(kind)),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = highlightTextColor(),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                items(group, key = { "${it.kind}-${it.id}" }) { item ->
                                    SearchRow(item = item, query = q, onClick = { onNavigate(targetOf(item.kind)); onDismiss() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun kindLabel(kind: SearchKind): Int = when (kind) {
    SearchKind.Announcement -> R.string.title_announcements
    SearchKind.Event -> R.string.community_events
    SearchKind.Sermon -> R.string.sermons_title
    SearchKind.Lesson -> R.string.word_study
}

private fun targetOf(kind: SearchKind): HomeTarget = when (kind) {
    SearchKind.Announcement -> HomeTarget.Announcements
    SearchKind.Event -> HomeTarget.Events
    SearchKind.Sermon -> HomeTarget.Sermons
    SearchKind.Lesson -> HomeTarget.Study
}

@Composable
private fun SearchRow(item: SearchItem, query: String, onClick: () -> Unit) {
    val icon = when (item.kind) {
        SearchKind.Announcement -> Icons.Outlined.Campaign
        SearchKind.Event -> Icons.Outlined.Event
        SearchKind.Sermon -> Icons.Outlined.PlayCircle
        SearchKind.Lesson -> Icons.Outlined.MenuBook
    }
    WordCard(onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).background(highlightColor().copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = highlightTextColor()) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    item.date?.let { DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(it) },
                    item.extra.takeIf { it.isNotBlank() }
                ).joinToString("  \u00B7  ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val text = if (item.body.contains(query, ignoreCase = true)) snippetOf(item.body, query) else item.body.take(90)
                if (text.isNotBlank()) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
