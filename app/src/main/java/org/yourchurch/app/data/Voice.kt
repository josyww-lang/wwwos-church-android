package org.yourchurch.app.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** Records a voice message with the phone's microphone: a small AAC file, at most two minutes. */
class VoiceRecorder(private val context: Context) {
    companion object {
        const val MAX_MS = 120_000
        const val MIN_MS = 800
    }

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean {
        cancel()
        val out = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(32_000)
            r.setAudioEncodingBitRate(48_000)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            out.delete()
            false
        }
    }

    fun elapsedMs(): Int = if (recorder == null) 0 else (SystemClock.elapsedRealtime() - startedAt).toInt()

    /** Stops recording. Returns the file and its length in milliseconds, or null if it failed or was too short. */
    fun stop(): Pair<File, Int>? {
        val r = recorder ?: return null
        val out = file
        val length = elapsedMs()
        recorder = null
        file = null
        val ok = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false
        }
        runCatching { r.release() }
        if (!ok || out == null || length < MIN_MS) {
            out?.delete()
            return null
        }
        return out to length
    }

    /** Throws the recording away. */
    fun cancel() {
        stop()?.first?.delete()
    }
}
