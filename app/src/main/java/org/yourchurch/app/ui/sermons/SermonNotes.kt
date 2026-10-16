package org.yourchurch.app.ui.sermons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import org.yourchurch.app.R
import org.yourchurch.app.ui.chat.formatDuration

/**
 * Notes only the person can see, kept for each sermon. They save by themselves a moment after typing stops, and
 * "Add time" puts the current place in the sermon (like [12:34]) into the notes.
 */
@Composable
fun SermonNotes(sermonId: String, uid: String) {
    val ref = remember(sermonId, uid) {
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("sermonNotes").document(sermonId)
    }
    var text by rememberSaveable(sermonId) { mutableStateOf("") }
    var loaded by remember(sermonId) { mutableStateOf(false) }
    var lastSaved by remember(sermonId) { mutableStateOf("") }
    var saved by remember(sermonId) { mutableStateOf(false) }
    val controller by rememberMediaController()

    LaunchedEffect(ref) {
        val remote = runCatching { ref.get().await().getString("text") }.getOrNull().orEmpty()
        if (text.isEmpty()) text = remote
        lastSaved = remote
        loaded = true
    }
    LaunchedEffect(text, loaded) {
        if (!loaded || text == lastSaved) return@LaunchedEffect
        saved = false
        delay(1200)
        val ok = runCatching {
            if (text.isBlank()) ref.delete().await()
            else ref.set(mapOf("text" to text.take(5000), "updatedAt" to Timestamp.now())).await()
        }.isSuccess
        if (ok) lastSaved = text
        saved = ok
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.notes_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.notes_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(5000) },
                minLines = 4,
                placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val position = (controller?.currentPosition ?: 0L).coerceAtLeast(0L).toInt()
                    val separator = if (text.isEmpty() || text.endsWith("\n")) "" else "\n"
                    text = (text + separator + "[" + formatDuration(position) + "] ").take(5000)
                }) { Text(stringResource(R.string.notes_add_time)) }
                if (saved && text.isNotBlank()) {
                    Text(
                        stringResource(R.string.notes_saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
