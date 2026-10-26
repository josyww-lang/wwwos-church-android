package org.yourchurch.app.ui.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.yourchurch.app.R
import org.yourchurch.app.data.Lesson
import org.yourchurch.app.ui.chat.LinkifiedText
import org.yourchurch.app.ui.theme.highlightTextColor

internal fun lessonDate(date: Date?): String =
    if (date == null) "" else DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(date)

/** The lesson on a full page, so it is easy to read. Links in it can be tapped. */
@Composable
internal fun LessonDetail(lesson: Lesson, isAdmin: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                    Spacer(Modifier.weight(1f))
                    if (isAdmin) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.word_edit)) }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.word_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp)) {
                    if (lesson.passage.isNotBlank()) {
                        Text(lesson.passage.uppercase(), style = MaterialTheme.typography.labelLarge, color = highlightTextColor())
                    }
                    Text(lesson.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        lessonDate(lesson.createdAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    LinkifiedText(lesson.body, MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/** Write or change a lesson. */
@Composable
internal fun LessonForm(lesson: Lesson, onSave: (Lesson) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(lesson.title) }
    var passage by rememberSaveable { mutableStateOf(lesson.passage) }
    var body by rememberSaveable { mutableStateOf(lesson.body) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                    Text(stringResource(R.string.word_lesson_new), style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = title.isNotBlank(),
                        onClick = { onSave(Lesson(lesson.id, title.trim(), passage.trim(), body.trim(), lesson.createdAt, lesson.folderId)) }
                    ) { Text(stringResource(R.string.save)) }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it.take(120) }, singleLine = true,
                        label = { Text(stringResource(R.string.word_lesson_title)) }, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passage, onValueChange = { passage = it.take(80) }, singleLine = true,
                        label = { Text(stringResource(R.string.word_lesson_passage)) }, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body, onValueChange = { body = it.take(6000) }, minLines = 10,
                        label = { Text(stringResource(R.string.word_lesson_body)) }, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** A lesson in a list: the passage, the title, the first lines and the date. */
@Composable
internal fun LessonCard(lesson: Lesson, onClick: () -> Unit) {
    WordCard(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            if (lesson.passage.isNotBlank()) {
                Text(lesson.passage.uppercase(), style = MaterialTheme.typography.labelLarge, color = highlightTextColor())
            }
            Text(lesson.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (lesson.body.isNotBlank()) {
                Text(
                    lesson.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                lessonDate(lesson.createdAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
