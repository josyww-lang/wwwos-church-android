package org.yourchurch.app.ui.community

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.BibleLink
import org.yourchurch.app.data.WordViewModel
import org.yourchurch.app.ui.common.InfoHint
import org.yourchurch.app.ui.common.SkeletonCards

/**
 * Links to other Bible apps or websites. Only leaders see this list (from the button in the books panel of the Bible); they add,
 * change and remove the links. Members read the Bible in the app.
 */
@Composable
fun ReadPane(uid: String, isAdmin: Boolean, vm: WordViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLinkForm by remember { mutableStateOf(false) }
    var editingLink by remember { mutableStateOf<BibleLink?>(null) }
    var deleteLink by remember { mutableStateOf<BibleLink?>(null) }

    if (state.loading) {
        SkeletonCards()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Members read the Bible in the app, so the links to other Bible apps are only for the leaders (who can add them).
            if (isAdmin) item {
                Text(stringResource(R.string.word_bible_title), style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp))
                InfoHint(stringResource(R.string.word_bible_hint), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.word_links_leaders_only),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isAdmin && state.links.isEmpty()) {
                item {
                    Text(
                        stringResource(if (isAdmin) R.string.word_links_empty_admin else R.string.word_links_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(if (isAdmin) state.links else emptyList(), key = { "l" + it.id }) { link ->
                WordCard(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) } }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        WordIcon(Icons.Outlined.MenuBook)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(link.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (link.note.isNotBlank()) {
                                Text(
                                    link.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (isAdmin) {
                            LeaderMenu(onEdit = { editingLink = link; showLinkForm = true }, onDelete = { deleteLink = link })
                        } else {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (isAdmin) {
                item {
                    OutlinedButton(onClick = { editingLink = null; showLinkForm = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.word_add_link))
                    }
                }
            }
        }
    }

    if (showLinkForm) {
        LinkFormDialog(
            existing = editingLink,
            onSave = { link ->
                scope.launch { vm.saveLink(link, uid) }
                showLinkForm = false
            },
            onDismiss = { showLinkForm = false }
        )
    }
    deleteLink?.let { link ->
        ConfirmDelete(onConfirm = { vm.deleteLink(link); deleteLink = null }, onDismiss = { deleteLink = null })
    }
}

@Composable
private fun LinkFormDialog(existing: BibleLink?, onSave: (BibleLink) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var url by rememberSaveable { mutableStateOf(existing?.url ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.word_add_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(80) }, singleLine = true,
                    label = { Text(stringResource(R.string.word_link_title)) }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it.take(300); invalid = false }, singleLine = true,
                    label = { Text(stringResource(R.string.word_link_url)) }, isError = invalid,
                    supportingText = { if (invalid) Text(stringResource(R.string.word_link_invalid)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(200) },
                    label = { Text(stringResource(R.string.word_link_note)) }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && url.isNotBlank(),
                onClick = {
                    val clean = url.trim()
                    if (!(clean.startsWith("https://") || clean.startsWith("http://"))) {
                        invalid = true
                    } else {
                        onSave(BibleLink(existing?.id ?: "", title.trim(), clean, note.trim()))
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
internal fun ConfirmDelete(onConfirm: () -> Unit, onDismiss: () -> Unit, message: Int = R.string.word_confirm_delete) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.word_delete)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.word_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
