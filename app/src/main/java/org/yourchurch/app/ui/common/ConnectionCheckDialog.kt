package org.yourchurch.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.yourchurch.app.R

private class CheckLine(val label: Int, val ok: Boolean, val detail: String)

/**
 * Asks the church server a few simple questions and shows the answers, so a problem (a wrong account, no permission,
 * no connection) can be seen at once instead of guessed.
 */
@Composable
fun ConnectionCheckDialog(onDismiss: () -> Unit) {
    var lines by remember { mutableStateOf<List<CheckLine>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val user = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(tick) {
        lines = null
        val result = mutableListOf<CheckLine>()
        val uid = user?.uid
        val db = FirebaseFirestore.getInstance()
        fun reason(e: Throwable) = (e as? FirebaseFirestoreException)?.code?.name ?: (e.message ?: e.javaClass.simpleName).take(70)
        result += CheckLine(R.string.cc_signed_in, uid != null, user?.email ?: "no account")
        if (uid != null) {
            runCatching { withTimeout(15_000) { db.collection("users").document(uid).get(Source.SERVER).await() } }
                .onSuccess { result += CheckLine(R.string.cc_profile, it.exists(), it.getString("displayName") ?: "no profile found") }
                .onFailure { result += CheckLine(R.string.cc_profile, false, reason(it)) }
            runCatching { withTimeout(15_000) { db.collection("admins").document(uid).get(Source.SERVER).await().exists() } }
                .onSuccess { result += CheckLine(R.string.cc_admin, true, if (it) "yes" else "no") }
                .onFailure { result += CheckLine(R.string.cc_admin, false, reason(it)) }
            runCatching { withTimeout(15_000) { db.collection("approvedMembers").document(uid).get(Source.SERVER).await().exists() } }
                .onSuccess { result += CheckLine(R.string.cc_member, true, if (it) "yes" else "no") }
                .onFailure { result += CheckLine(R.string.cc_member, false, reason(it)) }
        }
        runCatching { withTimeout(15_000) { db.collection("settings").document("church").get(Source.SERVER).await() } }
            .onSuccess { result += CheckLine(R.string.cc_server, true, "ok") }
            .onFailure { result += CheckLine(R.string.cc_server, false, reason(it)) }
        lines = result
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cc_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.cc_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val shown = lines
                if (shown == null) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    shown.forEach { line ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            if (line.ok) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp))
                            else Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(stringResource(line.label), style = MaterialTheme.typography.titleSmall)
                                Text(line.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { tick++ }) { Text(stringResource(R.string.cc_again)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
