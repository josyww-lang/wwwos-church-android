package org.yourchurch.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.yourchurch.app.R
import org.yourchurch.app.data.DirectoryMember
import org.yourchurch.app.data.PersonRow
import org.yourchurch.app.ui.common.PersonAvatar

/**
 * A searchable list of approved members with checkboxes.
 * It explains itself when empty, and admins can approve waiting people right from here.
 */
@Composable
fun MemberPicker(
    candidates: List<DirectoryMember>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    loaded: Boolean,
    failed: Boolean,
    isAdmin: Boolean,
    pending: List<PersonRow>,
    onApprove: (PersonRow) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = candidates.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(60) },
            placeholder = { Text(stringResource(R.string.chat_search_people)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        when {
            failed -> Text(
                stringResource(R.string.picker_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
            !loaded -> CircularProgressIndicator(
                modifier = Modifier.padding(top = 16.dp).size(24.dp),
                strokeWidth = 2.dp
            )
            else -> LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                if (candidates.isEmpty()) {
                    item(key = "empty") {
                        val message = when {
                            !isAdmin -> R.string.picker_none_member
                            pending.isNotEmpty() -> R.string.picker_none_admin_pending
                            else -> R.string.picker_none_admin
                        }
                        Text(
                            stringResource(message),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else if (shown.isEmpty()) {
                    item(key = "nomatch") {
                        Text(
                            stringResource(R.string.picker_no_match),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                items(shown, key = { it.uid }) { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(member.uid) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = member.uid in selected, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        PersonAvatar(uid = member.uid, name = member.name, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(member.name.ifBlank { "?" })
                    }
                }

                // Admins can approve people who have signed up but aren't approved yet.
                if (isAdmin && pending.isNotEmpty()) {
                    item(key = "pendingTitle") {
                        Text(
                            stringResource(R.string.picker_pending_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(pending, key = { "p" + it.uid }) { person ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PersonAvatar(uid = person.uid, name = person.name, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.name.ifBlank { "?" })
                                if (person.email.isNotBlank()) {
                                    Text(
                                        person.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(onClick = { onApprove(person) }, shape = RoundedCornerShape(12.dp)) {
                                Text(stringResource(R.string.chat_approve))
                            }
                        }
                    }
                }
            }
        }
    }
}
