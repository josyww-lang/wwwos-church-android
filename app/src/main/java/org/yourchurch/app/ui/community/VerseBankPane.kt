package org.yourchurch.app.ui.community

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.BankVerse
import org.yourchurch.app.data.MAX_VERSE_DAYS
import org.yourchurch.app.data.VerseBankViewModel
import org.yourchurch.app.data.parseBankRows

/**
 * For leaders: the church's list of verses, one for each day, in Afaan Oromoo, Norsk and English. The welcome card shows
 * one a day in the person's language, and starts again after the last day. Rows copied from the spreadsheet can be pasted.
 */
@Composable
fun VerseBankPane(vm: VerseBankViewModel = viewModel(key = "verse-bank")) {
    val verses by vm.verses.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BankVerse?>(null) }
    var showImport by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                stringResource(R.string.verses_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showImport = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.verses_paste), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { editing = BankVerse(day = (verses.maxOfOrNull { it.day } ?: 0) + 1) },
                    enabled = (verses.maxOfOrNull { it.day } ?: 0) < MAX_VERSE_DAYS,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.verses_add), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (verses.isEmpty()) {
            item { Text(stringResource(R.string.verses_empty), style = MaterialTheme.typography.bodyLarge) }
        }
        items(verses, key = { it.day }) { v ->
            WordCard(onClick = { editing = v }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.verses_day, v.day) + "  \u00B7  " + listOf(v.refOm, v.refNb, v.refEn).firstOrNull { it.isNotBlank() }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        listOf("Afaan Oromoo" to v.textOm, "Norsk" to v.textNb, "English" to v.textEn)
                            .joinToString("     ") { (name, text) -> (if (text.isNotBlank()) "\u2713 " else "\u2013 ") + name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    editing?.let { v ->
        VerseDialog(vm = vm, verse = v, exists = verses.any { it.day == v.day }, onDismiss = { editing = null })
    }
    if (showImport) ImportDialog(vm = vm, onDismiss = { showImport = false })
}

@Composable
private fun VerseDialog(vm: VerseBankViewModel, verse: BankVerse, exists: Boolean, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var refOm by rememberSaveable { mutableStateOf(verse.refOm) }
    var textOm by rememberSaveable { mutableStateOf(verse.textOm) }
    var refNb by rememberSaveable { mutableStateOf(verse.refNb) }
    var textNb by rememberSaveable { mutableStateOf(verse.textNb) }
    var refEn by rememberSaveable { mutableStateOf(verse.refEn) }
    var textEn by rememberSaveable { mutableStateOf(verse.textEn) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.verses_day, verse.day)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageFields("Afaan Oromoo", refOm, { refOm = it }, textOm, { textOm = it })
                LanguageFields("Norsk", refNb, { refNb = it }, textNb, { textNb = it })
                LanguageFields("English", refEn, { refEn = it }, textEn, { textEn = it })
                if (exists) {
                    TextButton(onClick = { scope.launch { vm.delete(verse.day); onDismiss() } }) {
                        Text(stringResource(R.string.verses_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = textOm.isNotBlank() || textNb.isNotBlank() || textEn.isNotBlank(),
                onClick = {
                    scope.launch {
                        vm.save(listOf(BankVerse(verse.day, refOm.trim(), textOm.trim(), refNb.trim(), textNb.trim(), refEn.trim(), textEn.trim())))
                        onDismiss()
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun LanguageFields(name: String, ref: String, onRef: (String) -> Unit, text: String, onText: (String) -> Unit) {
    Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(
        value = ref, onValueChange = { onRef(it.take(60)) }, singleLine = true,
        label = { Text(stringResource(R.string.verses_ref)) }, modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = text, onValueChange = { onText(it.take(800)) }, minLines = 2,
        label = { Text(stringResource(R.string.verses_text)) }, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ImportDialog(vm: VerseBankViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pasted by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val rows = remember(pasted) { parseBankRows(pasted) }
    val done = stringResource(R.string.verses_import_done)
    val failed = stringResource(R.string.verses_import_failed)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.verses_paste)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.verses_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pasted, onValueChange = { pasted = it; message = null }, minLines = 6, maxLines = 8, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.verses_import_found, rows.size), style = MaterialTheme.typography.labelLarge)
                message?.let { Text(it, color = if (it == done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = rows.isNotEmpty() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = vm.save(rows)
                        busy = false
                        message = if (result.isSuccess) done else failed
                        if (result.isSuccess) pasted = ""
                    }
                }
            ) { Text(stringResource(R.string.verses_import_do)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
