@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.sermons

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.yourchurch.app.R
import org.yourchurch.app.data.SERMON_REACTIONS
import org.yourchurch.app.data.Sermon
import org.yourchurch.app.data.SermonComment
import org.yourchurch.app.data.SermonsState
import org.yourchurch.app.data.SermonsViewModel
import org.yourchurch.app.data.sermonLanguageLabel
import org.yourchurch.app.data.toLocalDate
import org.yourchurch.app.ui.chat.LinkifiedText
import org.yourchurch.app.ui.common.PersonAvatar

@Composable
fun SermonDetailScreen(
    sermon: Sermon,
    myUid: String,
    myName: String,
    isAdmin: Boolean,
    state: SermonsState,
    vm: SermonsViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    LaunchedEffect(sermon.id) { vm.openComments(sermon.id) }
    DisposableEffect(sermon.id) { onDispose { vm.closeComments() } }

    var commentText by rememberSaveable(sermon.id) { mutableStateOf("") }
    var replyTo by remember(sermon.id) { mutableStateOf<SermonComment?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SermonPill(
                    stringResource(sermonLanguageLabel(sermon.language)),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    MaterialTheme.colorScheme.primary
                )
                if (sermon.series.isNotBlank()) {
                    SermonPill(
                        sermon.series,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!sermon.published) {
                    SermonPill(
                        stringResource(R.string.sermon_draft),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text(sermon.title, style = MaterialTheme.typography.headlineSmall)

            val date = sermon.date.toLocalDate()
                .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault()))
            Text(
                if (sermon.speaker.isNotBlank()) "${sermon.speaker}  ·  $date" else date,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (sermon.description.isNotBlank()) {
                Text(sermon.description, style = MaterialTheme.typography.bodyLarge)
            }

            // The audio comes first, then the video.
            if (sermon.hasAudio) {
                SermonAudioPlayer(sermon)
            }

            // Private notes for this sermon
            SermonNotes(sermonId = sermon.id, uid = myUid)

            if (sermon.hasVideo) {
                Button(
                    onClick = {
                        val link = Uri.parse("https://www.youtube.com/watch?v=${sermon.youtubeId}")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link)) }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sermon_watch), fontWeight = FontWeight.Medium)
                }
            }

            // Reactions
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SERMON_REACTIONS.forEach { emoji ->
                    val people = sermon.reactions[emoji].orEmpty()
                    val mine = myUid in people
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (mine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { vm.react(sermon, emoji, myUid) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 20.sp)
                        if (people.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                people.size.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Comments
            Text(
                "${stringResource(R.string.sermon_comments)} (${state.comments.size})",
                style = MaterialTheme.typography.titleMedium
            )
            when {
                state.commentsDenied -> Text(
                    stringResource(R.string.sermon_comments_locked),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.commentsLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else -> {
                    if (state.comments.isEmpty()) {
                        Text(
                            stringResource(R.string.sermon_comments_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.comments.forEach { comment ->
                        CommentRow(
                            comment = comment,
                            myUid = myUid,
                            canDelete = isAdmin || comment.authorId == myUid,
                            onReply = { replyTo = comment },
                            onLike = { vm.toggleLike(sermon.id, comment, myUid) },
                            onDelete = { vm.deleteComment(sermon.id, comment) }
                        )
                    }

                    replyTo?.let { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${stringResource(R.string.sermon_replying_to, target.authorName)}: ${target.text}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { replyTo = null }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it.take(1000) },
                            placeholder = { Text(stringResource(R.string.sermon_comment_hint)) },
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val clean = commentText.trim()
                                if (clean.isNotEmpty()) {
                                    vm.postComment(sermon.id, clean, myUid, myName, replyTo)
                                    commentText = ""
                                    replyTo = null
                                }
                            },
                            enabled = commentText.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.chat_send),
                                tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CommentRow(
    comment: SermonComment,
    myUid: String,
    canDelete: Boolean,
    onReply: () -> Unit,
    onLike: () -> Unit,
    onDelete: () -> Unit
) {
    val liked = myUid in comment.likes
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        PersonAvatar(uid = comment.authorId, name = comment.authorName, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName.ifBlank { "?" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                comment.createdAt?.let { time ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        DateUtils.getRelativeTimeSpanString(time.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (comment.replyToName.isNotBlank()) {
                Text(
                    "${comment.replyToName}: ${comment.replyToText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            LinkifiedText(comment.text, MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (liked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                        .clickable { onLike() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\uD83D\uDC4D", fontSize = 16.sp)
                    if (comment.likes.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Text(comment.likes.size.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                }
                TextButton(onClick = onReply) { Text(stringResource(R.string.sermon_comment_reply)) }
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
