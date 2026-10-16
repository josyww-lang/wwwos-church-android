@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.sermons

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.yourchurch.app.LocaleManager
import org.yourchurch.app.R
import org.yourchurch.app.data.SERMON_LANGUAGES
import org.yourchurch.app.data.Sermon
import org.yourchurch.app.data.SermonsViewModel
import org.yourchurch.app.data.UploadEvent
import org.yourchurch.app.data.extractYoutubeId
import org.yourchurch.app.data.sermonLanguageLabel
import org.yourchurch.app.data.toLocalDate
import org.yourchurch.app.data.toStartOfDayDate

private const val MAX_AUDIO_BYTES = 100L * 1024 * 1024

private data class PickedFile(val name: String, val size: Long, val mime: String?)

private fun inspect(context: Context, uri: Uri): PickedFile {
    var name = ""
    var size = -1L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    return PickedFile(name, size, context.contentResolver.getType(uri))
}

/** Audio file types by file extension, for files the phone doesn't label as audio. */
private val audioByExtension = mapOf(
    "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "mp4" to "audio/mp4", "aac" to "audio/aac",
    "wav" to "audio/wav", "ogg" to "audio/ogg", "oga" to "audio/ogg", "opus" to "audio/ogg",
    "flac" to "audio/flac", "amr" to "audio/amr", "3gp" to "audio/3gpp", "3gpp" to "audio/3gpp",
    "wma" to "audio/x-ms-wma", "aiff" to "audio/aiff", "aif" to "audio/aiff", "mka" to "audio/x-matroska",
    "weba" to "audio/webm", "webm" to "audio/webm"
)

/**
 * Works out the audio type of a picked file. Any audio file is accepted. Some phones label audio files
 * "application/octet-stream" or "video/mp4" (voice recorders), so the file name is used too.
 */
private fun audioMime(file: PickedFile): String? {
    val mime = file.mime
    if (mime != null && mime.startsWith("audio/")) return mime
    return audioByExtension[file.name.substringAfterLast('.', "").lowercase()]
}

/** Admin-only form for adding or editing a sermon, including uploading its audio file. */
@Composable
fun SermonFormScreen(
    existing: Sermon?,
    authorUid: String,
    vm: SermonsViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val defaultLanguage = remember {
        when (LocaleManager.languageCode(context)) {
            "nb", "nn", "no" -> "nb"
            "om" -> "om"
            else -> "en"
        }
    }

    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var speaker by rememberSaveable { mutableStateOf(existing?.speaker ?: "") }
    var series by rememberSaveable { mutableStateOf(existing?.series ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var language by rememberSaveable { mutableStateOf(existing?.language ?: defaultLanguage) }
    var dateIso by rememberSaveable {
        mutableStateOf((existing?.date?.toLocalDate() ?: LocalDate.now()).toString())
    }
    var youtube by rememberSaveable {
        mutableStateOf(existing?.youtubeId?.let { "https://youtu.be/$it" } ?: "")
    }
    var audioLink by rememberSaveable { mutableStateOf(existing?.audioUrl ?: "") }
    var published by rememberSaveable { mutableStateOf(existing?.published ?: true) }

    var pickedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pickedName by rememberSaveable { mutableStateOf("") }
    var pickedMime by rememberSaveable { mutableStateOf("") }
    var removeAudio by rememberSaveable { mutableStateOf(false) }

    var showPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    val hadAudioFile = existing?.audioPath?.isNotEmpty() == true && !removeAudio

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val info = inspect(context, uri)
            val mime = audioMime(info)
            when {
                mime == null -> errorRes = R.string.sermon_audio_not_audio
                info.size >= MAX_AUDIO_BYTES -> errorRes = R.string.sermon_audio_too_big
                else -> {
                    pickedUri = uri
                    pickedName = info.name
                    pickedMime = mime
                    errorRes = null
                }
            }
        }
    }

    BackHandler(enabled = !saving, onBack = onClose)

    val youtubeId = extractYoutubeId(youtube)
    val youtubeBad = youtube.isNotBlank() && youtubeId == null
    val audioLinkBad = audioLink.isNotBlank() && !audioLink.trim().startsWith("https://")

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (existing == null) R.string.sermon_new else R.string.sermon_edit),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !saving) {
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
                value = title, onValueChange = { title = it.take(120) },
                label = { Text(stringResource(R.string.sermon_field_title)) },
                singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = speaker, onValueChange = { speaker = it.take(80) },
                label = { Text(stringResource(R.string.sermon_field_speaker)) },
                singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.sermon_field_language), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SERMON_LANGUAGES.forEach { code ->
                    FilterChip(
                        selected = language == code,
                        onClick = { language = code },
                        enabled = !saving,
                        label = { Text(stringResource(sermonLanguageLabel(code))) }
                    )
                }
            }

            Text(stringResource(R.string.sermon_field_date), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { showPicker = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(
                    LocalDate.parse(dateIso)
                        .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault()))
                )
            }

            OutlinedTextField(
                value = series, onValueChange = { series = it.take(80) },
                label = { Text(stringResource(R.string.sermon_field_series)) },
                singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it.take(2000) },
                label = { Text(stringResource(R.string.sermon_field_description)) },
                minLines = 3, enabled = !saving, modifier = Modifier.fillMaxWidth()
            )

            // ----- Audio -----
            Text(stringResource(R.string.sermon_audio_section), style = MaterialTheme.typography.titleMedium)

            if (pickedUri != null) {
                Text(pickedName.ifBlank { stringResource(R.string.sermon_audio_attached) })
            } else if (hadAudioFile) {
                Text(stringResource(R.string.sermon_audio_attached))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("audio/*", "video/mp4", "video/3gpp", "video/webm", "application/ogg", "application/octet-stream")) },
                    enabled = !saving
                ) {
                    Text(
                        stringResource(
                            if (pickedUri != null || hadAudioFile) R.string.sermon_replace_audio
                            else R.string.sermon_choose_audio
                        )
                    )
                }
                if (pickedUri != null || hadAudioFile) {
                    TextButton(
                        onClick = {
                            pickedUri = null
                            pickedName = ""
                            removeAudio = true
                        },
                        enabled = !saving
                    ) {
                        Text(stringResource(R.string.sermon_remove_audio), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Text(
                stringResource(R.string.sermon_audio_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = audioLink, onValueChange = { audioLink = it.take(500) },
                label = { Text(stringResource(R.string.sermon_field_audio_link)) },
                singleLine = true, enabled = !saving, isError = audioLinkBad,
                supportingText = { if (audioLinkBad) Text(stringResource(R.string.sermon_audio_bad)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = youtube, onValueChange = { youtube = it.take(300) },
                label = { Text(stringResource(R.string.sermon_field_youtube)) },
                singleLine = true, enabled = !saving, isError = youtubeBad,
                supportingText = {
                    when {
                        youtubeBad -> Text(stringResource(R.string.sermon_youtube_bad))
                        youtubeId != null -> Text(stringResource(R.string.sermon_youtube_ok))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.sermon_published), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = published, onCheckedChange = { published = it }, enabled = !saving)
            }

            if (saving && pickedUri != null) {
                Text(
                    stringResource(R.string.sermon_uploading, (progress * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }

            errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorRes = R.string.err_title_required
                        return@Button
                    }
                    if (youtubeBad) {
                        errorRes = R.string.sermon_youtube_bad
                        return@Button
                    }
                    if (audioLinkBad) {
                        errorRes = R.string.sermon_audio_bad
                        return@Button
                    }
                    errorRes = null
                    saving = true
                    progress = 0f

                    scope.launch {
                        val oldPath = existing?.audioPath.orEmpty()
                        var audioPath = if (removeAudio) "" else oldPath
                        var uploadedPath: String? = null

                        val uri = pickedUri
                        if (uri != null) {
                            vm.upload(uri, pickedMime).collect { event ->
                                when (event) {
                                    is UploadEvent.Progress -> progress = event.fraction
                                    is UploadEvent.Done -> uploadedPath = event.path
                                    is UploadEvent.Failed -> Unit
                                }
                            }
                            val path = uploadedPath
                            if (path == null) {
                                errorRes = R.string.sermon_upload_failed
                                saving = false
                                return@launch
                            }
                            audioPath = path
                        }

                        val sermon = Sermon(
                            id = existing?.id ?: "",
                            title = title.trim(),
                            speaker = speaker.trim(),
                            date = LocalDate.parse(dateIso).toStartOfDayDate(),
                            language = language,
                            series = series.trim(),
                            description = description.trim(),
                            youtubeId = youtubeId ?: "",
                            audioPath = audioPath,
                            audioUrl = audioLink.trim(),
                            published = published,
                            createdAt = existing?.createdAt ?: Date(),
                            authorId = existing?.authorId?.ifEmpty { authorUid } ?: authorUid
                        )
                        val result = vm.save(sermon)
                        saving = false
                        result.onSuccess {
                            if (oldPath.isNotEmpty() && oldPath != audioPath) vm.deleteAudio(oldPath)
                            onClose()
                        }.onFailure {
                            errorRes = R.string.err_save_failed
                            // Don't leave an orphaned upload behind.
                            uploadedPath?.let { vm.deleteAudio(it) }
                        }
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.sermon_save), fontWeight = FontWeight.Medium)
                }
            }
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
