package org.yourchurch.app.ui.announcements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.yourchurch.app.R
import org.yourchurch.app.data.Announcement
import org.yourchurch.app.data.AnnouncementsViewModel
import org.yourchurch.app.data.Category
import org.yourchurch.app.data.toLocalDate
import org.yourchurch.app.ui.common.ChurchName
import org.yourchurch.app.ui.common.EditedTag
import org.yourchurch.app.ui.common.SkeletonCards
import org.yourchurch.app.ui.theme.color
import org.yourchurch.app.ui.theme.heroBrush

/** Wraps "which announcement is being edited". A null [announcement] means a new one. */
private data class ComposeTarget(val announcement: Announcement?)

@Composable
fun AnnouncementsScreen(
    uid: String,
    isAdmin: Boolean,
    accountMenu: @Composable () -> Unit,
    /** False when a parent screen already shows the title and the account button. */
    showHeader: Boolean = true,
    /** Called with true while the "new announcement" form is open, so a parent can hide its own menus. */
    onEditing: (Boolean) -> Unit = {},
    // One view model per signed-in person, so listeners restart cleanly after signing out and in.
    vm: AnnouncementsViewModel = viewModel(key = uid)
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(isAdmin) { vm.allowScheduled(isAdmin) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.start() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<ComposeTarget?>(null) }
    var toDelete by remember { mutableStateOf<Announcement?>(null) }

    LaunchedEffect(editing != null) { onEditing(editing != null) }
    DisposableEffect(Unit) { onDispose { onEditing(false) } }

    val target = editing
    if (target != null) {
        ComposeScreen(
            existing = target.announcement,
            authorUid = uid,
            onSave = vm::save,
            onClose = { editing = null }
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { editing = ComposeTarget(null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_label), fontWeight = FontWeight.Medium) }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (showHeader) Header(accountMenu)
            PillTabs(
                labels = listOf(stringResource(R.string.tab_today), stringResource(R.string.tab_upcoming)),
                selected = tab,
                onSelect = { tab = it }
            )

            if (state.loadFailed) {
                Text(
                    stringResource(R.string.load_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            val list = if (tab == 0) state.today else state.upcoming
            when {
                state.loading -> SkeletonCards()
                list.isEmpty() -> EmptyState(
                    title = stringResource(if (tab == 0) R.string.empty_today else R.string.empty_upcoming),
                    hint = stringResource(if (isAdmin) R.string.empty_hint_admin else R.string.empty_hint_member)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(list, key = { _, a -> a.id }) { index, a ->
                        if (tab == 0 && index == 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HeroCard(
                                    a = a,
                                    isAdmin = isAdmin,
                                    onEdit = { editing = ComposeTarget(a) },
                                    onTogglePin = { vm.togglePin(a) },
                                    onDelete = { toDelete = a }
                                )
                            }
                        } else {
                            AnnouncementCard(
                                a = a,
                                showDate = tab == 1,
                                isAdmin = isAdmin,
                                onEdit = { editing = ComposeTarget(a) },
                                onTogglePin = { vm.togglePin(a) },
                                onDelete = { toDelete = a }
                            )
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(a.title) },
            confirmButton = {
                TextButton(onClick = { vm.delete(a); toDelete = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.keep)) }
            }
        )
    }
}

@Composable
private fun Header(accountMenu: @Composable () -> Unit) {
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
            Text(stringResource(R.string.title_announcements), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        }
        accountMenu()
    }
}

/** Two-option switch with a rounded highlight (Today / Upcoming). */
@Composable
private fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        RoundedCornerShape(11.dp)
                    )
                    .clickable(role = Role.Tab, onClick = { onSelect(i) })
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
        Text(
            hint,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Pill(text: String, background: Color, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** The first announcement of the day, shown as a large blue banner. */
@Composable
private fun HeroCard(
    a: Announcement,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val category = Category.from(a.category)
    val textButtonColors = ButtonDefaults.textButtonColors(contentColor = Color.White)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(heroBrush(), RoundedCornerShape(24.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(stringResource(category.labelRes), Color.White.copy(alpha = 0.22f), Color.White)
            if (a.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.pinned), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(a.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        if (a.message.isNotBlank()) {
            Text(a.message, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
        }
        if (a.edited) {
            EditedTag(
                current = if (a.message.isBlank()) a.title else a.title + "\n\n" + a.message,
                history = a.history,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
        if (isAdmin) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onTogglePin, colors = textButtonColors) {
                    Text(stringResource(if (a.pinned) R.string.unpin else R.string.pin))
                }
                TextButton(onClick = onEdit, colors = textButtonColors) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onDelete, colors = textButtonColors) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    a: Announcement,
    showDate: Boolean,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val category = Category.from(a.category)
    val accent = MaterialTheme.colorScheme.secondary
    val tint = category.color()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        // A light tint of the announcement's own colour (Prayer, Event, Urgent ...).
        colors = CardDefaults.cardColors(containerColor = lerp(MaterialTheme.colorScheme.surface, tint, 0.09f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, tint.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(stringResource(category.labelRes), category.color().copy(alpha = 0.14f), category.color())
                if (a.pinned) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PushPin, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.pinned), color = accent, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (showDate) {
                Text(
                    a.date.toLocalDate().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (a.time.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(a.time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(a.title, style = MaterialTheme.typography.titleLarge)
            if (a.message.isNotBlank()) {
                Text(a.message, style = MaterialTheme.typography.bodyLarge)
            }
            if (a.edited) {
                EditedTag(
                    current = if (a.message.isBlank()) a.title else a.title + "\n\n" + a.message,
                    history = a.history,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onTogglePin) {
                        Text(stringResource(if (a.pinned) R.string.unpin else R.string.pin))
                    }
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
