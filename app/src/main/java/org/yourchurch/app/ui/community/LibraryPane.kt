package org.yourchurch.app.ui.community

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.Lesson
import org.yourchurch.app.data.LibraryFolder
import org.yourchurch.app.data.LibraryItem
import org.yourchurch.app.data.VoiceRecorder
import org.yourchurch.app.data.WordViewModel
import org.yourchurch.app.ui.chat.formatDuration
import org.yourchurch.app.ui.common.SkeletonCards

/**
 * Library: folders with PDF files, audio files and voice notes. Leaders create folders, upload several files at once
 * and record voice notes; everyone can open, listen and read.
 */
@Composable
fun LibraryPane(uid: String, isAdmin: Boolean, canAdd: Boolean, vm: WordViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var folderId by rememberSaveable { mutableStateOf("") }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var renameItem by remember { mutableStateOf<LibraryItem?>(null) }
    var deleteFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var deleteItem by remember { mutableStateOf<LibraryItem?>(null) }
    var showVoice by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var uploadTotal by remember { mutableIntStateOf(0) }
    var uploadDone by remember { mutableIntStateOf(0) }
    var uploadFailed by remember { mutableIntStateOf(0) }
    var openingId by remember { mutableStateOf<String?>(null) }
    var openLesson by remember { mutableStateOf<Lesson?>(null) }
    var editLesson by remember { mutableStateOf<Lesson?>(null) }
    var deleteLesson by remember { mutableStateOf<Lesson?>(null) }
    val openFailed = stringResource(R.string.word_pdf_open_failed)

    val startUpload: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty() && !uploading) {
            uploading = true
            uploadTotal = uris.size
            uploadDone = 0
            uploadFailed = 0
            val target = folderId
            scope.launch {
                uris.forEach { uri ->
                    if (vm.uploadItem(context, uri, target, uid).isFailure) uploadFailed++
                    uploadDone++
                }
                uploading = false
            }
        }
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> startUpload(uris) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> startUpload(uris) }
    DisposableEffect(Unit) { onDispose { LibraryPlayer.stop() } }

    val folder = state.folders.firstOrNull { it.id == folderId }
    // Everything lives inside a folder. Anything added before folders existed is gathered in "Other files", to tidy up.
    val atRoot = folderId.isEmpty()
    val inOther = folderId == OTHER_FILES
    val shownFolder = if (inOther) "" else folderId
    // If the open folder was deleted, go back to the library.
    LaunchedEffect(folder, state.loading) { if (folderId.isNotEmpty() && folderId != OTHER_FILES && folder == null && !state.loading) folderId = "" }
    val allItems = state.items + state.pdfs.map {
        LibraryItem(it.id, it.title, "pdf", it.path, it.size, 0, "", it.createdAt, legacy = true)
    }
    val visible = if (atRoot) emptyList() else allItems.filter { it.folderId == shownFolder }.sortedByDescending { it.createdAt?.time ?: 0L }
    val lessonsHere = if (atRoot) emptyList() else state.lessons.filter { it.folderId == shownFolder }
    val looseCount = allItems.count { it.folderId == "" } + state.lessons.count { it.folderId == "" }

    if (state.loading) {
        SkeletonCards()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                if (folder != null || inOther) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { folderId = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                        }
                        Text(folder?.name ?: stringResource(R.string.word_other_files), style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (isAdmin && folder != null) LeaderMenu(onEdit = { renameFolder = folder }, onDelete = { deleteFolder = folder })
                    }
                } else {
                    Column {
                        Text(stringResource(R.string.word_study), style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp))
                        Text(
                            stringResource(R.string.word_study_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (uploading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.word_uploading_n, (uploadDone + 1).coerceAtMost(uploadTotal), uploadTotal))
                    }
                }
            } else if (uploadFailed > 0) {
                item {
                    Text(
                        stringResource(R.string.word_upload_failed_n, uploadFailed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (atRoot) {
                items(state.folders, key = { "f" + it.id }) { f ->
                    WordCard(onClick = { folderId = f.id }) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            WordIcon(Icons.Outlined.Folder)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.word_files_count, allItems.count { it.folderId == f.id } + state.lessons.count { it.folderId == f.id }),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isAdmin) {
                                LeaderMenu(onEdit = { renameFolder = f }, onDelete = { deleteFolder = f })
                            } else {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (atRoot && looseCount > 0) {
                item(key = "other-files") {
                    WordCard(onClick = { folderId = OTHER_FILES }) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            WordIcon(Icons.Outlined.Folder)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.word_other_files), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), maxLines = 1)
                                Text(
                                    stringResource(R.string.word_files_count, looseCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (visible.isEmpty() && lessonsHere.isEmpty() && (!atRoot || (state.folders.isEmpty() && looseCount == 0))) {
                item {
                    Text(
                        stringResource(if (!atRoot) R.string.word_empty_folder else R.string.word_library_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(lessonsHere, key = { "s" + it.id }) { lesson -> LessonCard(lesson = lesson, onClick = { openLesson = lesson }) }
            items(visible, key = { "i" + it.id }) { item ->
                val isAudio = item.kind != "pdf"
                // Leaders manage everything; a member who may add content manages what they added.
                val mine = isAdmin || (item.authorId.isNotEmpty() && item.authorId == uid)
                val playing = LibraryPlayer.playingId == item.id
                val loading = LibraryPlayer.loadingId == item.id || openingId == item.id
                WordCard(onClick = {
                    if (isAudio) {
                        LibraryPlayer.toggle(item.id) { vm.downloadItem(context, item) }
                    } else if (openingId == null) {
                        openingId = item.id
                        scope.launch {
                            val file = vm.downloadItem(context, item)
                            openingId = null
                            if (file == null || !openPdfFile(context, file)) {
                                Toast.makeText(context, openFailed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WordIcon(
                                when (item.kind) {
                                    "voice" -> Icons.Outlined.Mic
                                    "audio" -> Icons.Outlined.Headphones
                                    else -> Icons.Outlined.PictureAsPdf
                                }
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        item.kind == "voice" -> stringResource(R.string.word_kind_voice) + " \u00B7 " + formatDuration(item.durationMs)
                                        item.kind == "audio" -> stringResource(R.string.word_kind_audio) +
                                            (if (item.durationMs > 0) " \u00B7 " + formatDuration(item.durationMs) else "")
                                        else -> stringResource(R.string.word_kind_pdf) + " \u00B7 " + formatSize(item.size)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            when {
                                loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                isAudio -> Box(
                                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                !mine -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (mine) {
                                LeaderMenu(
                                    onEdit = if (item.legacy) null else { { renameItem = item } },
                                    onDelete = { deleteItem = item }
                                )
                            }
                        }
                        if (playing && item.durationMs > 0) {
                            LinearProgressIndicator(
                                progress = { (LibraryPlayer.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
            // At the top only a folder can be made. Lessons, files, audio and voice notes are added inside a folder.
            if (canAdd && atRoot) {
                item {
                    OutlinedButton(onClick = { showNewFolder = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.word_new_folder))
                    }
                }
            }
            if (canAdd && folder != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { editLesson = Lesson(folderId = folderId) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.word_lesson_new), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { showVoice = true }, enabled = !uploading, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.word_voice_note), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }, enabled = !uploading, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.word_upload_pdf), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }, enabled = !uploading, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.word_upload_audio), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        NameDialog(
            title = R.string.word_new_folder,
            label = R.string.word_folder_name,
            initial = "",
            onSave = { name -> scope.launch { vm.createFolder(name, uid) }; showNewFolder = false },
            onDismiss = { showNewFolder = false }
        )
    }
    renameFolder?.let { f ->
        NameDialog(
            title = R.string.word_rename,
            label = R.string.word_folder_name,
            initial = f.name,
            onSave = { vm.renameFolder(f, it); renameFolder = null },
            onDismiss = { renameFolder = null }
        )
    }
    renameItem?.let { i ->
        NameDialog(
            title = R.string.word_rename,
            label = R.string.word_pdf_name,
            initial = i.title,
            onSave = { vm.renameItem(i, it); renameItem = null },
            onDismiss = { renameItem = null }
        )
    }
    deleteFolder?.let { f ->
        ConfirmDelete(
            message = R.string.word_folder_delete_confirm,
            onConfirm = {
                vm.deleteFolder(f, allItems.filter { it.folderId == f.id }, state.lessons.filter { it.folderId == f.id })
                deleteFolder = null
                folderId = ""
            },
            onDismiss = { deleteFolder = null }
        )
    }
    deleteItem?.let { i ->
        ConfirmDelete(onConfirm = { LibraryPlayer.stop(); vm.deleteItem(i); deleteItem = null }, onDismiss = { deleteItem = null })
    }
    openLesson?.let { lesson ->
        LessonDetail(
            lesson = lesson,
            isAdmin = isAdmin || (lesson.authorId.isNotEmpty() && lesson.authorId == uid),
            onEdit = { editLesson = lesson; openLesson = null },
            onDelete = { deleteLesson = lesson; openLesson = null },
            onClose = { openLesson = null }
        )
    }
    editLesson?.let { lesson ->
        LessonForm(
            lesson = lesson,
            onSave = { saved ->
                scope.launch { vm.saveLesson(saved, uid) }
                editLesson = null
            },
            onDismiss = { editLesson = null }
        )
    }
    deleteLesson?.let { lesson ->
        ConfirmDelete(onConfirm = { vm.deleteLesson(lesson); deleteLesson = null }, onDismiss = { deleteLesson = null })
    }
    if (showVoice) {
        VoiceNoteDialog(
            onSave = { file, ms, title ->
                showVoice = false
                uploading = true
                uploadTotal = 1
                uploadDone = 0
                uploadFailed = 0
                val target = folderId
                scope.launch {
                    val result = vm.uploadVoice(file, ms, title, target, uid)
                    runCatching { file.delete() }
                    if (result.isFailure) uploadFailed = 1
                    uploadDone = 1
                    uploading = false
                }
            },
            onDismiss = { showVoice = false }
        )
    }
}

@Composable
private fun NameDialog(title: Int, label: Int, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                singleLine = true,
                label = { Text(stringResource(label)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Record a voice note (up to two minutes), give it a name, and add it to the library. */
@Composable
private fun VoiceNoteDialog(onSave: (File, Int, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var phase by remember { mutableIntStateOf(0) }          // 0 ready, 1 recording, 2 recorded
    var elapsed by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Pair<File, Int>?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    val denied = stringResource(R.string.chat_voice_denied)
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    val stopRecording: () -> Unit = {
        result = recorder.stop()
        phase = if (result != null) 2 else 0
    }
    LaunchedEffect(phase) {
        if (phase == 1) {
            while (phase == 1) {
                elapsed = recorder.elapsedMs()
                if (elapsed >= VoiceRecorder.MAX_MS) {
                    stopRecording()
                    break
                }
                delay(200)
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (recorder.start()) phase = 1 } else Toast.makeText(context, denied, Toast.LENGTH_LONG).show()
    }
    val begin: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (recorder.start()) phase = 1
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    AlertDialog(
        onDismissRequest = { recorder.cancel(); result?.first?.delete(); onDismiss() },
        title = { Text(stringResource(R.string.word_voice_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                when (phase) {
                    0 -> {
                        Text(stringResource(R.string.word_voice_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = begin),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.word_record), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(34.dp)) }
                    }
                    1 -> {
                        Text(formatDuration(elapsed), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error).clickable(onClick = stopRecording),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.word_stop), tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(34.dp)) }
                    }
                    else -> {
                        Text(formatDuration(result?.second ?: 0), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(80) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.word_pdf_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (phase == 2) {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { result?.let { (file, ms) -> onSave(file, ms, name.trim()) } }
                ) { Text(stringResource(R.string.word_upload_files)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { recorder.cancel(); result?.first?.delete(); onDismiss() }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Opens a PDF in the phone's PDF viewer. Returns false if the phone has none. */
private fun openPdfFile(context: Context, file: File): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
}.getOrDefault(false)

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%d KB", (bytes / 1024).coerceAtLeast(1))
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}


/** The folder that gathers what was added before folders existed. */
private const val OTHER_FILES = "_other"
