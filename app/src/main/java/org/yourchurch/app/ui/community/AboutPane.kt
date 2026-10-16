package org.yourchurch.app.ui.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yourchurch.app.LocaleManager
import org.yourchurch.app.R
import org.yourchurch.app.data.AboutSection
import org.yourchurch.app.data.AboutViewModel

/**
 * About the church: sections that leaders write (for example Our history, What we believe, Our vision, Our leaders),
 * each with a title and a text in English, Norsk and Afaan Oromoo. Everyone reads them in their own language.
 */
@Composable
fun AboutPane(isAdmin: Boolean, vm: AboutViewModel = viewModel(key = "about")) {
    val sections by vm.sections.collectAsStateWithLifecycle()
    val language = LocaleManager.languageCode(LocalContext.current)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AboutSection?>(null) }
    var deleting by remember { mutableStateOf<AboutSection?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (isAdmin) {
                    IconButton(onClick = { editing = AboutSection(order = (sections.maxOfOrNull { it.order } ?: -1) + 1) }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.about_add))
                    }
                }
            }
        }
        if (sections.isEmpty()) {
            item {
                Text(
                    stringResource(if (isAdmin) R.string.about_empty_admin else R.string.about_empty_member),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(sections, key = { it.id }) { section ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            section.titleFor(language),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (isAdmin) {
                            IconButton(onClick = { scope.launch { vm.move(section, -1) } }) {
                                Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.about_up))
                            }
                            IconButton(onClick = { scope.launch { vm.move(section, 1) } }) {
                                Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.about_down))
                            }
                            LeaderMenu(onEdit = { editing = section }, onDelete = { deleting = section })
                        }
                    }
                    Text(
                        section.bodyFor(language),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
    editing?.let { section ->
        AboutDialog(section = section, vm = vm, onDismiss = { editing = null })
    }
    deleting?.let { section ->
        ConfirmDelete(onConfirm = { scope.launch { vm.delete(section.id) }; deleting = null }, onDismiss = { deleting = null })
    }
}

@Composable
private fun AboutDialog(section: AboutSection, vm: AboutViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var titleEn by rememberSaveable { mutableStateOf(section.titleEn) }
    var titleNb by rememberSaveable { mutableStateOf(section.titleNb) }
    var titleOm by rememberSaveable { mutableStateOf(section.titleOm) }
    var bodyEn by rememberSaveable { mutableStateOf(section.bodyEn) }
    var bodyNb by rememberSaveable { mutableStateOf(section.bodyNb) }
    var bodyOm by rememberSaveable { mutableStateOf(section.bodyOm) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (section.id.isBlank()) R.string.about_add else R.string.about_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutFields("Afaan Oromoo", titleOm, { titleOm = it }, bodyOm, { bodyOm = it })
                AboutFields("Norsk", titleNb, { titleNb = it }, bodyNb, { bodyNb = it })
                AboutFields("English", titleEn, { titleEn = it }, bodyEn, { bodyEn = it })
                if (failed) Text(stringResource(R.string.err_save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = titleEn.isNotBlank() || titleNb.isNotBlank() || titleOm.isNotBlank(),
                onClick = {
                    scope.launch {
                        val result = vm.save(
                            section.copy(
                                titleEn = titleEn.trim(), titleNb = titleNb.trim(), titleOm = titleOm.trim(),
                                bodyEn = bodyEn.trim(), bodyNb = bodyNb.trim(), bodyOm = bodyOm.trim()
                            )
                        )
                        if (result.isSuccess) onDismiss() else failed = true
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun AboutFields(language: String, title: String, onTitle: (String) -> Unit, body: String, onBody: (String) -> Unit) {
    Text(language, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(
        value = title, onValueChange = { onTitle(it.take(120)) }, singleLine = true,
        label = { Text(stringResource(R.string.about_field_title)) }, modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = body, onValueChange = { onBody(it.take(15000)) }, minLines = 5,
        label = { Text(stringResource(R.string.about_field_body)) }, modifier = Modifier.fillMaxWidth()
    )
}
