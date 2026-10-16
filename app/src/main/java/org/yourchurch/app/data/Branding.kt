package org.yourchurch.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** The church logo the leaders uploaded. Everyone can see it, also before signing in. */
object BrandingStore {
    var logo by mutableStateOf<ImageBitmap?>(null)
        private set

    /** The colour combination the leaders chose for the church (an index into the list in Appearance), or null if none. */
    var churchPalette by mutableStateOf<Int?>(null)
        private set

    private var registration: ListenerRegistration? = null
    private val doc get() = FirebaseFirestore.getInstance().collection("settings").document("branding")

    fun start() {
        if (registration != null) return
        registration = doc.addSnapshotListener { snapshot, _ ->
            val bytes = snapshot?.getBlob("logo")?.toBytes()
            logo = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            churchPalette = snapshot?.getLong("palette")?.toInt()
        }
    }

    /** Shrinks the picture (keeping any see-through background) and saves it. Returns false if that did not work. */
    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = encode(context, uri) ?: return@withContext false
        runCatching {
            withTimeout(20_000) {
                doc.set(mapOf("logo" to Blob.fromBytes(bytes), "updatedAt" to Timestamp.now()), SetOptions.merge()).await()
            }
        }.isSuccess
    }

    suspend fun remove(): Boolean = runCatching {
        withTimeout(15_000) { doc.update(mapOf("logo" to FieldValue.delete(), "updatedAt" to Timestamp.now())).await() }
    }.isSuccess

    /** Leaders: choose the colour combination everyone starts with. */
    suspend fun setPalette(index: Int): Boolean = runCatching {
        withTimeout(15_000) { doc.set(mapOf("palette" to index, "updatedAt" to Timestamp.now()), SetOptions.merge()).await() }
    }.isSuccess

    private fun encode(context: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        for (side in intArrayOf(256, 192, 128, 96)) {
            val scale = minOf(side.toFloat() / decoded.width, side.toFloat() / decoded.height, 1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else decoded
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            if (out.size() <= 85_000) return out.toByteArray()
        }
        return null
    }
}
