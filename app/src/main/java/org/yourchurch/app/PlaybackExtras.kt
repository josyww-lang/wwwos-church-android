package org.yourchurch.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.yourchurch.app.data.Sermon

/** Stops the sermon audio after a while, or at the end of the sermon that is playing. */
object SleepTimer {
    /** When the timer ends (0 = no timer). */
    var endsAt by mutableLongStateOf(0L)
        private set
    var stopAtEnd by mutableStateOf(false)
        private set
    internal var player: Player? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun startMinutes(minutes: Int) {
        cancel()
        endsAt = System.currentTimeMillis() + minutes * 60_000L
        job = scope.launch {
            delay(minutes * 60_000L)
            player?.pause()
            endsAt = 0L
        }
    }

    fun startAtEnd() {
        cancel()
        stopAtEnd = true
    }

    fun cancel() {
        job?.cancel()
        job = null
        endsAt = 0L
        stopAtEnd = false
    }
}

class QueuedSermon(val id: String, val title: String, val speaker: String, val audioPath: String, val audioUrl: String)

/** Sermons lined up to play one after the other. Kept on the phone. */
object SermonQueue {
    val items = mutableStateListOf<QueuedSermon>()
    private var loaded = false

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val array = JSONArray(prefs(context).getString("sermon_queue", "[]"))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                items.add(QueuedSermon(o.getString("id"), o.optString("title"), o.optString("speaker"), o.optString("audioPath"), o.optString("audioUrl")))
            }
        }
    }

    private fun save(context: Context) {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().put("id", it.id).put("title", it.title).put("speaker", it.speaker).put("audioPath", it.audioPath).put("audioUrl", it.audioUrl))
        }
        prefs(context).edit().putString("sermon_queue", array.toString()).apply()
    }

    fun contains(id: String) = items.any { it.id == id }

    fun add(context: Context, sermon: Sermon) {
        load(context)
        if (contains(sermon.id)) return
        items.add(QueuedSermon(sermon.id, sermon.title, sermon.speaker, sermon.audioPath, sermon.audioUrl))
        save(context)
    }

    fun remove(context: Context, id: String) {
        load(context)
        items.removeAll { it.id == id }
        save(context)
    }

    fun clear(context: Context) {
        items.clear()
        save(context)
    }

    /** Called by the playback service when a sermon ends: plays the first sermon in the queue. */
    suspend fun playNext(context: Context, player: Player) {
        load(context)
        val next = items.firstOrNull() ?: return
        items.removeAt(0)
        save(context)
        val uri: Uri? = when {
            SermonDownloads.isDownloaded(context, next.id) -> Uri.fromFile(SermonDownloads.file(context, next.id))
            next.audioPath.isNotEmpty() ->
                runCatching { FirebaseStorage.getInstance().reference.child(next.audioPath).downloadUrl.await() }.getOrNull()
            next.audioUrl.isNotEmpty() -> runCatching { Uri.parse(next.audioUrl) }.getOrNull()
            else -> null
        }
        if (uri == null) {
            playNext(context, player)   // skip one that cannot be played
            return
        }
        val position = prefs(context).getLong("sermon_pos_${next.id}", 0L)
        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId(next.id)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(next.title).setArtist(next.speaker.ifBlank { null }).build()
                )
                .build(),
            position
        )
        player.prepare()
        player.play()
    }
}

/** Sermon audio saved on the phone, so it plays without internet. */
object SermonDownloads {
    /** Changes whenever a download finishes or is removed. */
    var version by mutableIntStateOf(0)
        private set

    fun file(context: Context, id: String): File =
        File(File(context.filesDir, "sermons").apply { mkdirs() }, "$id.audio")

    fun isDownloaded(context: Context, id: String): Boolean = file(context, id).let { it.exists() && it.length() > 0 }

    fun remove(context: Context, id: String) {
        file(context, id).delete()
        version++
    }

    /** Saves the sermon's audio on the phone. [onProgress] gets 0 to 100 (only for uploaded files). */
    suspend fun download(context: Context, sermon: Sermon, onProgress: (Int) -> Unit): Boolean {
        val ok = withContext(Dispatchers.IO) {
            val target = file(context, sermon.id)
            val temp = File(target.parentFile, "${sermon.id}.part")
            val done = runCatching {
                if (sermon.audioPath.isNotEmpty()) {
                    val task = FirebaseStorage.getInstance().reference.child(sermon.audioPath).getFile(temp)
                    task.addOnProgressListener { s ->
                        if (s.totalByteCount > 0) onProgress((s.bytesTransferred * 100 / s.totalByteCount).toInt())
                    }
                    task.await()
                } else {
                    java.net.URL(sermon.audioUrl).openStream().use { input -> temp.outputStream().use { out -> input.copyTo(out) } }
                }
                check(temp.length() > 0)
                check(temp.renameTo(target))
            }.isSuccess
            if (!done) temp.delete()
            done
        }
        if (ok) version++
        return ok
    }
}
