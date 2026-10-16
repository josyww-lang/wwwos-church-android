@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.community

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.Prayer
import org.yourchurch.app.data.PrayersViewModel
import org.yourchurch.app.ui.chat.formatWhen
import org.yourchurch.app.ui.common.EditedTag
import org.yourchurch.app.ui.common.PersonAvatar
import org.yourchurch.app.ui.common.SkeletonCards

@Composable
fun PrayerPane(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    vm: PrayersViewModel = viewModel(key = "prayers-$uid")
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(uid, isAdmin) { vm.start(uid, isAdmin) }

    var showForm by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Prayer?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showForm = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.prayer_new), fontWeight = FontWeight.Medium) }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonCards(Modifier.align(Alignment.TopCenter))
                state.loadFailed -> Text(
                    stringResource(R.string.prayers_load_failed),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                state.church.isEmpty() && state.leaders.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.prayer_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        stringResource(R.string.prayer_empty_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.leaders.isNotEmpty()) {
                        item(key = "leadersTitle") {
                            Text(
                                stringResource(if (isAdmin) R.string.prayer_for_leaders else R.string.prayer_my_private),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(state.leaders, key = { "l" + it.id }) { prayer ->
                            PrayerCard(prayer, uid, isAdmin, vm) { toDelete = prayer }
                        }
                    }
                    items(state.church, key = { "c" + it.id }) { prayer ->
                        PrayerCard(prayer, uid, isAdmin, vm) { toDelete = prayer }
                    }
                }
            }
        }
    }

    if (showForm) {
        PrayerForm(uid = uid, myName = myName, vm = vm, onDismiss = { showForm = false })
    }

    toDelete?.let { prayer ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.prayer_delete_title)) },
            text = { Text(prayer.text.take(120)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(prayer); toDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.keep)) } }
        )
    }
}

@Composable
private fun PrayerCard(prayer: Prayer, uid: String, isAdmin: Boolean, vm: PrayersViewModel, onDelete: () -> Unit) {
    val mine = prayer.authorId == uid
    val iPrayed = uid in prayer.prayedBy
    val canManage = mine || isAdmin
    val hidden = prayer.anonymous || prayer.authorName.isBlank()
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hidden) {
                    Box(
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("?", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    PersonAvatar(uid = prayer.authorId, name = prayer.authorName, size = 36.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val name = if (hidden) {
                        stringResource(R.string.prayer_someone) + if (mine) " (${stringResource(R.string.chat_you)})" else ""
                    } else prayer.authorName
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        formatWhen(prayer.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (prayer.answered) {
                    Text(
                        stringResource(R.string.prayer_answered),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (canManage) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (mine) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_edit)) },
                                    onClick = { menuOpen = false; editing = true }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (prayer.answered) R.string.prayer_reopen else R.string.prayer_mark_answered))
                                },
                                onClick = { menuOpen = false; vm.setAnswered(prayer, !prayer.answered) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
            }

            Text(prayer.text, style = MaterialTheme.typography.bodyLarge)
            if (prayer.edited) {
                EditedTag(current = prayer.text, history = prayer.history, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editing) {
                PrayerEditDialog(
                    initial = prayer.text,
                    onSave = { vm.edit(prayer, it); editing = false },
                    onDismiss = { editing = false }
                )
            }

            // Three ways to respond: I prayed, I care, Praise God.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReactionChip("\uD83D\uDE4F " + stringResource(R.string.prayer_prayed), iPrayed, Modifier.weight(1f)) { vm.togglePrayed(prayer, uid) }
                ReactionChip("\u2764\uFE0F " + stringResource(R.string.prayer_care), uid in prayer.cares, Modifier.weight(1f)) { vm.toggleReaction(prayer, uid, "cares") }
                ReactionChip("\uD83D\uDE4C " + stringResource(R.string.prayer_praise), uid in prayer.praise, Modifier.weight(1f)) { vm.toggleReaction(prayer, uid, "praise") }
            }
            val counts = listOfNotNull(
                prayer.prayedBy.size.takeIf { it > 0 }?.let { "\uD83D\uDE4F $it" },
                prayer.cares.size.takeIf { it > 0 }?.let { "\u2764\uFE0F $it" },
                prayer.praise.size.takeIf { it > 0 }?.let { "\uD83D\uDE4C $it" }
            )
            if (counts.isNotEmpty()) {
                Text(
                    counts.joinToString("     "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrayerForm(uid: String, myName: String, vm: PrayersViewModel, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var anonymous by rememberSaveable { mutableStateOf(false) }
    var leadersOnly by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.prayer_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(1000) },
                    label = { Text(stringResource(R.string.prayer_field_text)) },
                    minLines = 4,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.prayer_anonymous), modifier = Modifier.weight(1f).padding(end = 12.dp))
                    Switch(checked = anonymous, onCheckedChange = { anonymous = it }, enabled = !saving)
                }
                if (anonymous) {
                    Text(
                        stringResource(R.string.prayer_anonymous_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(stringResource(R.string.prayer_audience), style = MaterialTheme.typography.titleSmall)
                listOf(false to R.string.prayer_audience_church, true to R.string.prayer_audience_leaders)
                    .forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = leadersOnly == value,
                                    enabled = !saving,
                                    role = Role.RadioButton,
                                    onClick = { leadersOnly = value }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = leadersOnly == value, onClick = null, enabled = !saving)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(label))
                        }
                    }
                if (failed) {
                    Text(stringResource(R.string.prayer_post_failed), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saving = true
                    failed = false
                    scope.launch {
                        vm.post(text.trim(), uid, myName, anonymous, leadersOnly)
                            .onSuccess { onDismiss() }
                            .onFailure { saving = false; failed = true }
                    }
                },
                enabled = text.isNotBlank() && !saving
            ) { Text(stringResource(R.string.prayer_post)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
        }
    )
}


@Composable
private fun PrayerEditDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prayer_edit_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(1000) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value.trim()) },
                enabled = value.isNotBlank() && value.trim() != initial.trim()
            ) { Text(stringResource(R.string.edit_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}


@Composable
private fun ReactionChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier
    )
}
