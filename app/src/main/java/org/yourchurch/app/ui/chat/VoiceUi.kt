package org.yourchurch.app.ui.chat

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.ChatImages
import org.yourchurch.app.data.ChatMessage

/** "0:07" or "1:25". */
internal fun formatDuration(ms: Int): String {
    val seconds = ms / 1000
    return String.format("%d:%02d", seconds / 60, seconds % 60)
}

/** Plays one voice message at a time. Opening another one, or leaving the chat, stops the current one. */
object VoicePlayback {
    var playingId by mutableStateOf<String?>(null)
        private set
    var loadingId by mutableStateOf<String?>(null)
        private set
    var positionMs by mutableIntStateOf(0)
        private set

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun toggle(context: Context, message: ChatMessage) {
        if (playingId == message.id) {
            stop()
            return
        }
        stop()
        loadingId = message.id
        scope.launch {
            val file = ChatImages.loadAudio(context.applicationContext, message.audioPath, message.id)
            loadingId = null
            if (file == null) return@launch
            val mp = MediaPlayer()
            try {
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                mp.start()
            } catch (e: Exception) {
                runCatching { mp.release() }
                return@launch
            }
            mp.setOnCompletionListener { stop() }
            player = mp
            playingId = message.id
            positionMs = 0
            ticker = scope.launch {
                while (isActive) {
                    positionMs = runCatching { mp.currentPosition }.getOrDefault(0)
                    delay(150)
                }
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingId = null
        loadingId = null
        positionMs = 0
    }
}

/** A voice message inside a chat bubble: a play button, a progress line and the length. */
@Composable
fun VoiceBubble(message: ChatMessage, color: Color) {
    val context = LocalContext.current
    val playing = VoicePlayback.playingId == message.id
    val loading = VoicePlayback.loadingId == message.id
    val progress = if (playing && message.audioMs > 0) (VoicePlayback.positionMs.toFloat() / message.audioMs).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier.widthIn(min = 190.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(color.copy(alpha = 0.18f), CircleShape)
                .clickable { VoicePlayback.toggle(context, message) },
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = color)
            } else {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.chat_voice),
                    tint = color,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatDuration(if (playing) VoicePlayback.positionMs else message.audioMs),
                style = MaterialTheme.typography.labelMedium,
                color = color.copy(alpha = 0.9f)
            )
        }
    }
}

/** Shown in place of the message box while recording: bin it, watch the time, or send it. */
@Composable
fun RecordingBar(elapsedMs: Int, accent: Color, onCancel: () -> Unit, onSend: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recPulse"
    )
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.chat_voice_cancel), tint = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.width(4.dp))
        Spacer(Modifier.size(10.dp).background(Color(0xFFC62828).copy(alpha = pulse), CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(formatDuration(elapsedMs), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier.size(48.dp).background(accent, CircleShape).clickable(onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.chat_voice_send),
                tint = Color.White
            )
        }
    }
}
