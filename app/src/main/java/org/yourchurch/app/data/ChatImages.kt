package org.yourchurch.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Photos in chat. A picture is shrunk on the phone, the full picture goes into private storage (only the
 * people in the conversation can open it), and a small preview is kept with the message itself.
 */
object ChatImages {
    class Prepared(val full: ByteArray, val thumb: ByteArray)

    private const val FULL_SIDE = 1280
    private const val THUMB_SIDE = 360
    private val cache = LruCache<String, Bitmap>(12)

    /** Shrinks a picked picture and turns it upright. Returns null if it can't be read. */
    suspend fun prepare(context: Context, uri: Uri): Prepared? = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = decodeUpright(context, uri, FULL_SIDE) ?: return@runCatching null
            Prepared(
                full = compressUnder(bitmap, 1_500_000, 82),
                thumb = compressUnder(scaleDown(bitmap, THUMB_SIDE), 38_000, 72)
            )
        }.getOrNull()
    }

    /** A medium-size picture for the "about to send" preview. */
    suspend fun preview(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.Default) {
        runCatching { decodeUpright(context, uri, 900) }.getOrNull()
    }

    suspend fun upload(path: String, bytes: ByteArray) {
        withTimeout(60_000) {
            FirebaseStorage.getInstance().reference.child(path)
                .putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build())
                .await()
        }
    }

    /** Uploads a voice message recording. */
    suspend fun uploadAudio(path: String, bytes: ByteArray) {
        withTimeout(60_000) {
            FirebaseStorage.getInstance().reference.child(path)
                .putBytes(bytes, StorageMetadata.Builder().setContentType("audio/mp4").build())
                .await()
        }
    }

    /** Downloads a voice message once and keeps it on the phone, so playing it again is instant. */
    suspend fun loadAudio(context: Context, path: String, messageId: String): java.io.File? {
        val dir = java.io.File(context.cacheDir, "voice").apply { mkdirs() }
        val file = java.io.File(dir, "$messageId.m4a")
        if (file.exists() && file.length() > 0) return file
        return runCatching {
            val bytes = FirebaseStorage.getInstance().reference.child(path).getBytes(3L * 1024 * 1024).await()
            withContext(Dispatchers.IO) { file.writeBytes(bytes) }
            file
        }.getOrNull()
    }

    /** Downloads the full picture (through the signed-in account, so the storage rules apply). */
    suspend fun loadFull(path: String): Bitmap? {
        cache.get(path)?.let { return it }
        return runCatching {
            val bytes = FirebaseStorage.getInstance().reference.child(path).getBytes(6L * 1024 * 1024).await()
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                ?.also { cache.put(path, it) }
        }.getOrNull()
    }

    suspend fun deleteFile(path: String) {
        runCatching { withTimeout(15_000) { FirebaseStorage.getInstance().reference.child(path).delete().await() } }
    }

    private fun decodeUpright(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        val upright = if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        return scaleDown(upright, maxSide)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun compressUnder(bitmap: Bitmap, maxBytes: Int, startQuality: Int): ByteArray {
        var quality = startQuality
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality >= 20)
        return bytes
    }
}
