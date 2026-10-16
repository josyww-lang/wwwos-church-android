package org.yourchurch.app.ui.community

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Plays one library audio file or voice note at a time. Starting another one, or leaving the page, stops the first. */
object LibraryPlayer {
    var playingId by mutableStateOf<String?>(null)
        private set
    var loadingId by mutableStateOf<String?>(null)
        private set
    var positionMs by mutableIntStateOf(0)
        private set

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun toggle(id: String, load: suspend () -> File?) {
        if (playingId == id) {
            stop()
            return
        }
        stop()
        loadingId = id
        scope.launch {
            val file = load()
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
            playingId = id
            positionMs = 0
            ticker = scope.launch {
                while (isActive) {
                    positionMs = runCatching { mp.currentPosition }.getOrDefault(0)
                    delay(200)
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
