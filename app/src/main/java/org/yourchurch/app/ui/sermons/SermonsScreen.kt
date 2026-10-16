@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.sermons

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.yourchurch.app.R
import org.yourchurch.app.SermonQueue
import org.yourchurch.app.data.Sermon
import org.yourchurch.app.data.SermonsViewModel
import org.yourchurch.app.data.sermonLanguageLabel
import org.yourchurch.app.data.toLocalDate
import org.yourchurch.app.ui.common.ChurchName
import org.yourchurch.app.ui.theme.highlightTextColor

/** Wraps "which sermon is being edited". A null [sermon] means a new one. */
private data class SermonTarget(val sermon: Sermon?)

@Composable
fun SermonsScreen(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    accountMenu: @Composable () -> Unit,
    showHeader: Boolean = true,
    // One view model per signed-in person, so listeners restart cleanly after signing out and in.
    vm: SermonsViewModel = viewModel(key = "sermons-$uid")
) {
    val state by vm.state.collectAsStateWithLifecycle()

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SermonTarget?>(null) }
    var toDelete by remember { mutableStateOf<Sermon?>(null) }
    var language by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }

    val editTarget = editing
    if (editTarget != null) {
        SermonFormScreen(
            existing = editTarget.sermon,
            authorUid = uid,
            vm = vm,
            onClose = { editing = null }
        )
        return
    }

    val selected = state.sermons.firstOrNull { it.id == selectedId }
    if (selected != null) {
        SermonDetailScreen(
            sermon = selected,
            myUid = uid,
            myName = myName,
            isAdmin = isAdmin,
            state = state,
            vm = vm,
            onBack = { selectedId = null },
            onEdit = { editing = SermonTarget(selected) },
            onDelete = { toDelete = selected }
        )
    } else {
        val shown = state.sermons.filter { it.published || isAdmin }
        val visible = shown.filter { sermon ->
            (language == "all" || sermon.language == language) &&
                (query.isBlank() ||
                    sermon.title.contains(query, ignoreCase = true) ||
                    sermon.speaker.contains(query, ignoreCase = true) ||
                    sermon.series.contains(query, ignoreCase = true))
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (isAdmin) {
                    ExtendedFloatingActionButton(
                        onClick = { editing = SermonTarget(null) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(18.dp),
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.sermon_new), fontWeight = FontWeight.Medium) }
                    )
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (showHeader) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(48.dp))  // balances the account icon, so the name is truly centred
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            ChurchName()
                            Text(stringResource(R.string.sermons_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        }
                        accountMenu()
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    placeholder = { Text(stringResource(R.string.sermons_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(12.dp))

                if (state.loadFailed) {
                    Text(
                        stringResource(R.string.sermons_load_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    shown.isEmpty() -> EmptyState(
                        title = stringResource(R.string.sermons_empty_title),
                        hint = stringResource(if (isAdmin) R.string.sermons_empty_admin else R.string.sermons_empty_member)
                    )
                    visible.isEmpty() -> EmptyState(
                        title = stringResource(R.string.sermons_no_match),
                        hint = ""
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (SermonQueue.items.isNotEmpty()) item(key = "up-next") { UpNextCard(onOpen = { selectedId = it }) }
                        // Sermons are grouped by month, newest first, so it is easy to see which are which.
                        var lastMonth: YearMonth? = null
                        visible.forEach { sermon ->
                            val month = YearMonth.from(sermon.date.toLocalDate())
                            if (month != lastMonth) {
                                lastMonth = month
                                item(key = "month-$month") { MonthHeader(month) }
                            }
                            item(key = sermon.id) { SermonCard(sermon = sermon, onClick = { selectedId = sermon.id }) }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { sermon ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.sermon_delete_title)) },
            text = { Text("${sermon.title}\n\n${stringResource(R.string.sermon_delete_text)}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(sermon)
                    if (selectedId == sermon.id) selectedId = null
                    toDelete = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.keep)) }
            }
        )
    }
}

@Composable
private fun EmptyState(title: String, hint: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (hint.isNotEmpty()) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun SermonPill(text: String, background: Color, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun SermonCard(sermon: Sermon, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SermonPill(
                    stringResource(sermonLanguageLabel(sermon.language)),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    MaterialTheme.colorScheme.primary
                )
                if (sermon.series.isNotBlank()) {
                    SermonPill(
                        sermon.series,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!sermon.published) {
                    SermonPill(
                        stringResource(R.string.sermon_draft),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text(sermon.title, style = MaterialTheme.typography.titleLarge)

            val date = sermon.date.toLocalDate()
                .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (sermon.speaker.isNotBlank()) "${sermon.speaker}  ·  $date" else date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (sermon.hasAudio) {
                    Icon(Icons.Outlined.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                if (sermon.hasVideo) {
                    Icon(Icons.Outlined.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}


@Composable
private fun MonthHeader(month: YearMonth) {
    Text(
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())).replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.titleSmall,
        color = highlightTextColor(),
        modifier = Modifier.padding(top = 6.dp)
    )
}
