package org.yourchurch.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Profile pictures are shrunk to this many pixels square and stored as a small JPEG in the database. */
private const val AVATAR_SIZE = 192
private const val MAX_AVATAR_BYTES = 40_000

/**
 * Holds the profile pictures that have been loaded since the app opened.
 * Pictures live in the Firestore collection "avatars" (one small document per person), so no
 * file storage and no paid plan is needed. A picture is fetched once per person per app session.
 */
object AvatarStore {
    /** Loaded pictures by user id. Screens read this directly and update when it changes. */
    val bitmaps = mutableStateMapOf<String, Bitmap>()

    private val checked = HashSet<String>()
    private val db get() = FirebaseFirestore.getInstance()

    /** Loads this person's picture if we haven't looked yet this session. */
    suspend fun ensure(uid: String) {
        if (uid.isEmpty() || uid in checked) return
        checked.add(uid)
        try {
            val snapshot = withTimeout(15_000) { db.collection("avatars").document(uid).get().await() }
            val bytes = snapshot.getBlob("photo")?.toBytes()
            if (bytes != null) {
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                if (bitmap != null) bitmaps[uid] = bitmap
            }
        } catch (e: CancellationException) {
            checked.remove(uid)
            throw e
        } catch (e: Exception) {
            checked.remove(uid) // try again next time it's needed
        }
    }

    /** Loads a picked photo upright (at most about 1,200 pixels on its longest side) so the person can move and zoom it first. */
    suspend fun loadForCrop(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.Default) { loadUprightBitmap(context.applicationContext, uri, 1200) }

    /** Saves a square picture the person has already framed (moved and zoomed) as their profile picture. */
    suspend fun uploadFromBitmap(uid: String, square: Bitmap): Boolean {
        val bytes = withContext(Dispatchers.Default) { squareToAvatarBytes(square) } ?: return false
        val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            ?: return false
        return try {
            withTimeout(20_000) {
                db.collection("avatars").document(uid)
                    .set(mapOf("photo" to Blob.fromBytes(bytes), "updatedAt" to FieldValue.serverTimestamp()))
                    .await()
            }
            checked.add(uid)
            bitmaps[uid] = bitmap
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Removes a picture. People can remove their own, and admins can remove anyone's. */
    suspend fun delete(uid: String): Boolean = try {
        withTimeout(15_000) { db.collection("avatars").document(uid).delete().await() }
        checked.add(uid)
        bitmaps.remove(uid)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** Forget everything (used when someone signs out). */
    fun clear() {
        bitmaps.clear()
        checked.clear()
    }
}

/** Loads a picked photo with the right side up, shrunk so that its longest side is about [maxSide] pixels. */
private fun loadUprightBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val loaded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return@runCatching null
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
    Bitmap.createBitmap(loaded, 0, 0, loaded.width, loaded.height, matrix, true)
}.getOrNull()

/** Scales a framed square picture down to the profile picture size and compresses it (lower quality if still too big). */
private fun squareToAvatarBytes(square: Bitmap): ByteArray? = runCatching {
    val result = Bitmap.createBitmap(AVATAR_SIZE, AVATAR_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(square, Rect(0, 0, square.width, square.height), Rect(0, 0, AVATAR_SIZE, AVATAR_SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
    var quality = 85
    var data = ByteArray(0)
    do {
        val stream = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        data = stream.toByteArray()
        quality -= 15
    } while (data.size > MAX_AVATAR_BYTES && quality > 20)
    if (data.size > MAX_AVATAR_BYTES) null else data
}.getOrNull()
