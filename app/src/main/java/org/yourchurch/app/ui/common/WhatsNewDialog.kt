package org.yourchurch.app.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.yourchurch.app.R

/**
 * "What's new" is shown once to everyone who updates the app. To show a new one: write the new items below (and their
 * texts in strings.xml, in all three languages) and raise [VERSION] by one. People who install the app fresh do not see
 * it (they get the welcome slides instead); More > What's new shows it again whenever someone wants to read it.
 */
object WhatsNew {
    const val VERSION = 1
    private const val FILE = "settings"
    private const val KEY = "whats_new_seen"

    fun shouldShow(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY, 0) < VERSION

    fun markSeen(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY, VERSION).apply()
    }
}

private class Note(val icon: ImageVector, @StringRes val title: Int, @StringRes val text: Int)

private val NOTES = listOf(
    Note(Icons.Outlined.MenuBook, R.string.whatsnew_reading_title, R.string.whatsnew_reading_text),
    Note(Icons.Outlined.Search, R.string.whatsnew_search_title, R.string.whatsnew_search_text),
    Note(Icons.Outlined.Event, R.string.whatsnew_events_title, R.string.whatsnew_events_text),
    Note(Icons.Outlined.Headphones, R.string.whatsnew_sermons_title, R.string.whatsnew_sermons_text),
    Note(Icons.Outlined.ChatBubbleOutline, R.string.whatsnew_chat_title, R.string.whatsnew_chat_text),
    Note(Icons.Outlined.Widgets, R.string.whatsnew_widget_title, R.string.whatsnew_widget_text),
    Note(Icons.Outlined.Palette, R.string.whatsnew_look_title, R.string.whatsnew_look_text)
)

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whatsnew_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NOTES.forEach { note ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(note.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(note.title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(note.text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.whatsnew_gotit)) } }
    )
}
