package org.yourchurch.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Locale
import org.yourchurch.app.R
import org.yourchurch.app.data.EditVersion

/** The small word "edited". Tap it to see the earlier versions of the text. */
@Composable
fun EditedTag(current: String, history: List<EditVersion>, color: Color, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    Text(
        stringResource(R.string.edited_label),
        style = MaterialTheme.typography.labelSmall,
        fontStyle = FontStyle.Italic,
        textDecoration = TextDecoration.Underline,
        color = color,
        modifier = modifier.clickable { show = true }
    )
    if (show) EditHistoryDialog(current, history) { show = false }
}

@Composable
fun EditHistoryDialog(current: String, history: List<EditVersion>, onDismiss: () -> Unit) {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_history_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text(
                        stringResource(R.string.edit_history_current),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(current, style = MaterialTheme.typography.bodyLarge)
                }
                // Newest earlier version first.
                history.asReversed().forEach { version ->
                    Column {
                        Text(
                            stringResource(
                                R.string.edit_history_before,
                                version.at?.let { formatter.format(it) } ?: ""
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(version.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
