@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.sermons

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.yourchurch.app.PlaybackService
import org.yourchurch.app.R
import org.yourchurch.app.SermonDownloads
import org.yourchurch.app.SermonQueue
import org.yourchurch.app.SleepTimer
import org.yourchurch.app.data.Sermon

private sealed interface AudioSource {
    data object Loading : AudioSource
    data class Ready(val uri: Uri) : AudioSource
    data object Failed : AudioSource
}

/** Remembers where you stopped in each sermon, on this phone. */
private object SermonProgress {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun get(context: Context, sermonId: String): Long = prefs(context).getLong("sermon_pos_$sermonId", 0L)

    fun save(context: Context, sermonId: String, positionMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && positionMs >= durationMs - 15_000
        val edit = prefs(context).edit()
        if (positionMs < 5_000 || finished) edit.remove("sermon_pos_$sermonId")
        else edit.putLong("sermon_pos_$sermonId", positionMs)
        edit.apply()
    }
}

/** Connects to the playback service, which keeps playing when the screen is locked. */
@Composable
internal fun rememberMediaController(): State<MediaController?> {
    val context = LocalContext.current
    val controller = remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { runCatching { controller.value = future.get() } },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            controller.value = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

/** Plays a sermon's audio: an uploaded file (from Firebase Storage) or an external https link. */
@Composable
fun SermonAudioPlayer(sermon: Sermon) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf<AudioSource>(AudioSource.Loading) }
    val controller by rememberMediaController()

    LaunchedEffect(sermon.audioPath, sermon.audioUrl, attempt) {
        source = AudioSource.Loading
        source = if (SermonDownloads.isDownloaded(context, sermon.id)) {
            // Saved on this phone: plays without internet.
            AudioSource.Ready(Uri.fromFile(SermonDownloads.file(context, sermon.id)))
        } else if (sermon.audioPath.isNotEmpty()) {
            runCatching {
                FirebaseStorage.getInstance().reference.child(sermon.audioPath).downloadUrl.await()
            }.fold({ AudioSource.Ready(it) }, { AudioSource.Failed })
        } else {
            runCatching { Uri.parse(sermon.audioUrl) }.fold({ AudioSource.Ready(it) }, { AudioSource.Failed })
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.sermon_audio), style = MaterialTheme.typography.titleMedium)
            when (val s = source) {
                AudioSource.Loading -> Loading()
                AudioSource.Failed -> ErrorWithRetry(onRetry = { attempt++ })
                is AudioSource.Ready -> {
                    val c = controller
                    if (c == null) Loading() else {
                        PlayerControls(c, s.uri, sermon)
                        PlayerExtras(sermon)
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.sermon_audio_loading))
    }
}

@Composable
private fun ErrorWithRetry(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.sermon_audio_error), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.sermon_retry)) }
    }
}

@Composable
private fun PlayerControls(player: MediaController, uri: Uri, sermon: Sermon) {
    val context = LocalContext.current
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var failed by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var positionMs by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var dragging by remember { mutableStateOf(false) }
    var speedIndex by remember {
        mutableIntStateOf(speeds.indexOfFirst { it == player.playbackParameters.speed }.takeIf { it >= 0 } ?: 1)
    }

    fun load() {
        failed = false
        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId(sermon.id)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(sermon.title)
                        .setArtist(sermon.speaker.ifBlank { null })
                        .build()
                )
                .build(),
            SermonProgress.get(context, sermon.id)
        )
        player.prepare()
    }

    // Load this sermon, unless it is already the one in the player (so reopening it doesn't restart the sound).
    LaunchedEffect(player, sermon.id, uri) {
        if (player.currentMediaItem?.mediaId != sermon.id) load()
        isPlaying = player.isPlaying
        durationMs = player.duration.coerceAtLeast(0L)
        positionMs = player.currentPosition.coerceAtLeast(0L)
    }

    DisposableEffect(player, sermon.id) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && player.currentMediaItem?.mediaId == sermon.id) {
                    SermonProgress.save(context, sermon.id, player.currentPosition, player.duration)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) {
                    SermonProgress.save(context, sermon.id, player.duration, player.duration)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            // Leave the sound playing: it continues in the background. Just remember where we are.
            player.removeListener(listener)
            if (player.currentMediaItem?.mediaId == sermon.id) {
                SermonProgress.save(context, sermon.id, player.currentPosition, player.duration)
            }
        }
    }

    LaunchedEffect(player, isPlaying, dragging) {
        while (isPlaying && !dragging) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    if (failed) {
        ErrorWithRetry(onRetry = { load() })
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Slider(
            value = positionMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
            onValueChange = {
                dragging = true
                positionMs = it.toLong()
            },
            onValueChangeFinished = {
                player.seekTo(positionMs)
                dragging = false
            },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            enabled = durationMs > 0
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val back15 = stringResource(R.string.sermon_back15)
            TextButton(
                onClick = {
                    val target = (player.currentPosition - 15_000L).coerceAtLeast(0L)
                    player.seekTo(target)
                    positionMs = target
                },
                modifier = Modifier.semantics { contentDescription = back15 }
            ) {
                Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("15")
            }

            FilledIconButton(
                onClick = { if (isPlaying) player.pause() else player.play() },
                modifier = Modifier.size(64.dp)
            ) {
                if (buffering && !isPlaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.sermon_pause else R.string.sermon_play),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            val forward30 = stringResource(R.string.sermon_forward30)
            TextButton(
                onClick = {
                    val limit = if (durationMs > 0) durationMs else Long.MAX_VALUE
                    val target = (player.currentPosition + 30_000L).coerceAtMost(limit)
                    player.seekTo(target)
                    positionMs = target
                },
                modifier = Modifier.semantics { contentDescription = forward30 }
            ) {
                Icon(Icons.Filled.Forward30, contentDescription = null, modifier = Modifier.size(24.dp))
            }

            val speedLabel = speeds[speedIndex].toString().removeSuffix(".0") + "x"
            val speedDescription = stringResource(R.string.sermon_speed)
            TextButton(
                onClick = {
                    speedIndex = (speedIndex + 1) % speeds.size
                    player.setPlaybackSpeed(speeds[speedIndex])
                },
                modifier = Modifier.semantics { contentDescription = "$speedDescription $speedLabel" }
            ) {
                Text(speedLabel)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}


/** Sleep timer, the queue and saving for offline listening. */
@Composable
private fun PlayerExtras(sermon: Sermon) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sleepMenu by remember { mutableStateOf(false) }
    var downloadMenu by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val downloaded = SermonDownloads.version >= 0 && SermonDownloads.isDownloaded(context, sermon.id)
    val queued = SermonQueue.contains(sermon.id)
    LaunchedEffect(Unit) { SermonQueue.load(context) }
    LaunchedEffect(SleepTimer.endsAt) {
        while (SleepTimer.endsAt > 0) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { sleepMenu = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.sermon_sleep), maxLines = 1)
                }
                DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sermon_sleep_min, minutes)) },
                            onClick = { SleepTimer.startMinutes(minutes); sleepMenu = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sermon_sleep_end)) },
                        onClick = { SleepTimer.startAtEnd(); sleepMenu = false }
                    )
                    if (SleepTimer.endsAt > 0 || SleepTimer.stopAtEnd) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sermon_sleep_off), color = MaterialTheme.colorScheme.error) },
                            onClick = { SleepTimer.cancel(); sleepMenu = false }
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { if (queued) SermonQueue.remove(context, sermon.id) else SermonQueue.add(context, sermon) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Icon(if (queued) Icons.Filled.Check else Icons.Outlined.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (queued) R.string.sermon_queue_in else R.string.sermon_queue_add), maxLines = 1)
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = {
                        if (downloaded) {
                            downloadMenu = true
                        } else if (progress == null) {
                            progress = 0
                            failed = false
                            scope.launch {
                                val ok = SermonDownloads.download(context, sermon) { progress = it }
                                progress = null
                                failed = !ok
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(if (downloaded) Icons.Outlined.DownloadDone else Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            progress != null -> "${progress}%"
                            downloaded -> stringResource(R.string.sermon_downloaded)
                            else -> stringResource(R.string.sermon_download)
                        },
                        maxLines = 1
                    )
                }
                DropdownMenu(expanded = downloadMenu, onDismissRequest = { downloadMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sermon_download_remove)) },
                        onClick = { SermonDownloads.remove(context, sermon.id); downloadMenu = false }
                    )
                }
            }
        }
        if (SleepTimer.endsAt > 0) {
            val minutes = ((SleepTimer.endsAt - now).coerceAtLeast(0L) / 60_000L + 1).toInt()
            Text(stringResource(R.string.sermon_sleep_active, minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (SleepTimer.stopAtEnd) {
            Text(stringResource(R.string.sermon_sleep_active_end), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (failed) {
            Text(stringResource(R.string.sermon_download_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
