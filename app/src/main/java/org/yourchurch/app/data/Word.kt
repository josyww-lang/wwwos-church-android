package org.yourchurch.app.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import java.io.File
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A link to a Bible app or website (for example the Afaan Oromoo Bible). Set by the leaders. */
data class BibleLink(val id: String = "", val title: String = "", val url: String = "", val note: String = "")

/** A PDF in the church library (a Bible, a study guide, a hymn book). Uploaded by the leaders. */
data class BiblePdf(
    val id: String = "",
    val title: String = "",
    val path: String = "",
    val size: Long = 0,
    val createdAt: Date? = null
)

/** A weekly Bible study lesson posted by the leaders. */
data class Lesson(
    val id: String = "",
    val title: String = "",
    val passage: String = "",
    val body: String = "",
    val createdAt: Date? = null,
    /** The study folder it belongs to ("" = not in a folder). */
    val folderId: String = "",
    val authorId: String = ""
)

/** A folder in the church library. */
data class LibraryFolder(val id: String = "", val name: String = "", val createdAt: Date? = null, val authorId: String = "")

/** A file in the library: a PDF, an audio file, or a voice note a leader recorded. kind is "pdf", "audio" or "voice". */
data class LibraryItem(
    val id: String = "",
    val title: String = "",
    val kind: String = "pdf",
    val path: String = "",
    val size: Long = 0,
    val durationMs: Int = 0,
    val folderId: String = "",
    val createdAt: Date? = null,
    /** True for PDFs uploaded before folders existed. */
    val legacy: Boolean = false,
    /** Who added it (a leader, or a member the leaders allowed to add Study content). */
    val authorId: String = ""
)

const val MAX_PDF_BYTES = 25L * 1024 * 1024
const val MAX_AUDIO_BYTES = 50L * 1024 * 1024
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac")

private fun displayNameOf(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull().orEmpty()

private fun audioLength(context: Context, uri: Uri): Int = runCatching {
    val retriever = android.media.MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
    } finally {
        retriever.release()
    }
}.getOrDefault(0)

class WordRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val links = db.collection("bibleLinks")
    private val pdfs = db.collection("biblePdfs")
    private val lessons = db.collection("lessons")
    private val folders = db.collection("libraryFolders")
    private val items = db.collection("libraryItems")

    private fun <T> listen(query: Query, map: (DocumentSnapshot) -> T): Flow<Result<List<T>>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
            } else if (snapshot != null) {
                trySend(Result.success(snapshot.documents.map(map)))
            }
        }
        awaitClose { registration.remove() }
    }

    fun observeLinks() = listen(links.orderBy("createdAt")) {
        BibleLink(it.id, it.getString("title") ?: "", it.getString("url") ?: "", it.getString("note") ?: "")
    }

    fun observePdfs() = listen(pdfs.orderBy("createdAt", Query.Direction.DESCENDING).limit(100)) {
        BiblePdf(
            id = it.id,
            title = it.getString("title") ?: "",
            path = it.getString("path") ?: "",
            size = it.getLong("size") ?: 0L,
            createdAt = it.getTimestamp("createdAt")?.toDate()
        )
    }

    fun observeLessons() = listen(lessons.orderBy("createdAt", Query.Direction.DESCENDING).limit(60)) {
        Lesson(
            id = it.id,
            title = it.getString("title") ?: "",
            passage = it.getString("passage") ?: "",
            body = it.getString("body") ?: "",
            createdAt = it.getTimestamp("createdAt")?.toDate(),
            folderId = it.getString("folderId") ?: "",
            authorId = it.getString("authorId") ?: ""
        )
    }

    suspend fun saveLink(link: BibleLink, authorUid: String) {
        withTimeout(15_000) {
            if (link.id.isEmpty()) {
                links.add(
                    mapOf("title" to link.title, "url" to link.url, "note" to link.note, "createdAt" to Timestamp.now(), "authorId" to authorUid)
                ).await()
            } else {
                links.document(link.id).update(mapOf("title" to link.title, "url" to link.url, "note" to link.note)).await()
            }
        }
    }

    suspend fun deleteLink(link: BibleLink) {
        withTimeout(15_000) { links.document(link.id).delete().await() }
    }

    /** Uploads a PDF the leader picked, then adds it to the library. */
    suspend fun uploadPdf(context: Context, uri: Uri, title: String, authorUid: String) {
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        require(size in 1..MAX_PDF_BYTES) { "PDF too large or unreadable" }
        val doc = pdfs.document()
        val path = "library/${doc.id}.pdf"
        val ref = FirebaseStorage.getInstance().reference.child(path)
        withTimeout(240_000) {
            ref.putFile(uri, StorageMetadata.Builder().setContentType("application/pdf").build()).await()
        }
        try {
            withTimeout(15_000) {
                doc.set(
                    mapOf("title" to title, "path" to path, "size" to size, "createdAt" to Timestamp.now(), "authorId" to authorUid)
                ).await()
            }
        } catch (e: Exception) {
            runCatching { ref.delete().await() }
            throw e
        }
    }

    suspend fun deletePdf(pdf: BiblePdf) {
        withTimeout(15_000) { pdfs.document(pdf.id).delete().await() }
        runCatching { withTimeout(15_000) { FirebaseStorage.getInstance().reference.child(pdf.path).delete().await() } }
    }

    /** Downloads a PDF once and keeps it on the phone, so opening it again is instant. */
    suspend fun downloadPdf(context: Context, pdf: BiblePdf): File? {
        val dir = File(context.cacheDir, "library").apply { mkdirs() }
        val file = File(dir, "${pdf.id}.pdf")
        if (file.exists() && (pdf.size <= 0 || file.length() == pdf.size)) return file
        return runCatching {
            withTimeout(180_000) { FirebaseStorage.getInstance().reference.child(pdf.path).getFile(file).await() }
            file
        }.getOrNull()
    }

    // ---------- Library: folders, files, audio and voice notes ----------

    fun observeFolders() = listen(folders.orderBy("createdAt")) {
        LibraryFolder(it.id, it.getString("name") ?: "", it.getTimestamp("createdAt")?.toDate(), it.getString("authorId") ?: "")
    }

    fun observeItems() = listen(items.orderBy("createdAt", Query.Direction.DESCENDING).limit(300)) {
        LibraryItem(
            id = it.id,
            title = it.getString("title") ?: "",
            kind = it.getString("kind") ?: "pdf",
            path = it.getString("path") ?: "",
            size = it.getLong("size") ?: 0L,
            durationMs = (it.getLong("durationMs") ?: 0L).toInt(),
            folderId = it.getString("folderId") ?: "",
            createdAt = it.getTimestamp("createdAt")?.toDate(),
            authorId = it.getString("authorId") ?: ""
        )
    }

    suspend fun createFolder(name: String, authorUid: String) {
        withTimeout(15_000) {
            folders.add(mapOf("name" to name, "createdAt" to Timestamp.now(), "authorId" to authorUid)).await()
        }
    }

    suspend fun renameFolder(folder: LibraryFolder, name: String) {
        withTimeout(15_000) { folders.document(folder.id).update("name", name).await() }
    }

    suspend fun renameItem(item: LibraryItem, title: String) {
        withTimeout(15_000) { items.document(item.id).update("title", title).await() }
    }

    suspend fun deleteItem(item: LibraryItem) {
        if (item.legacy) {
            deletePdf(BiblePdf(item.id, item.title, item.path, item.size, item.createdAt))
            return
        }
        withTimeout(15_000) { items.document(item.id).delete().await() }
        runCatching { withTimeout(15_000) { FirebaseStorage.getInstance().reference.child(item.path).delete().await() } }
    }

    /** Deletes a folder and every file in it. */
    suspend fun deleteFolder(folder: LibraryFolder, inside: List<LibraryItem>, insideLessons: List<Lesson> = emptyList()) {
        inside.forEach { runCatching { deleteItem(it) } }
        insideLessons.forEach { runCatching { deleteLesson(it) } }
        withTimeout(15_000) { folders.document(folder.id).delete().await() }
    }

    private suspend fun putItem(
        kind: String,
        title: String,
        extension: String,
        size: Long,
        durationMs: Int,
        folderId: String,
        authorUid: String,
        upload: suspend (StorageReference) -> Unit
    ) {
        val doc = items.document()
        val path = "library/${doc.id}.$extension"
        val ref = FirebaseStorage.getInstance().reference.child(path)
        withTimeout(600_000) { upload(ref) }
        try {
            withTimeout(15_000) {
                doc.set(
                    mapOf(
                        "title" to title, "kind" to kind, "path" to path, "size" to size, "durationMs" to durationMs,
                        "folderId" to folderId, "createdAt" to Timestamp.now(), "authorId" to authorUid
                    )
                ).await()
            }
        } catch (e: Exception) {
            runCatching { ref.delete().await() }
            throw e
        }
    }

    /** Uploads one PDF or audio file a leader picked. */
    suspend fun uploadItem(context: Context, uri: Uri, folderId: String, authorUid: String) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val kind = when {
            mime == "application/pdf" -> "pdf"
            mime.startsWith("audio/") -> "audio"
            else -> throw IllegalArgumentException("Only PDF and audio files can be added")
        }
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        require(size in 1..(if (kind == "pdf") MAX_PDF_BYTES else MAX_AUDIO_BYTES)) { "File too large or unreadable" }
        val fileName = displayNameOf(context, uri)
        val extension = if (kind == "pdf") "pdf" else
            fileName.substringAfterLast('.', "").lowercase().takeIf { it in AUDIO_EXTENSIONS } ?: "m4a"
        val durationMs = if (kind == "audio") audioLength(context, uri) else 0
        putItem(kind, fileName.substringBeforeLast('.').ifBlank { "File" }.take(80), extension, size, durationMs, folderId, authorUid) { ref ->
            ref.putFile(uri, StorageMetadata.Builder().setContentType(mime).build()).await()
        }
    }

    /** Uploads a voice note a leader recorded in the app. */
    suspend fun uploadVoice(file: File, durationMs: Int, title: String, folderId: String, authorUid: String) {
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        putItem("voice", title.take(80), "m4a", bytes.size.toLong(), durationMs, folderId, authorUid) { ref ->
            ref.putBytes(bytes, StorageMetadata.Builder().setContentType("audio/mp4").build()).await()
        }
    }

    /** Downloads a library file once and keeps it on the phone, so it opens or plays instantly next time. */
    suspend fun downloadItem(context: Context, item: LibraryItem): File? {
        val dir = File(context.cacheDir, "library").apply { mkdirs() }
        val file = File(dir, "${item.id}.${item.path.substringAfterLast('.', "bin")}")
        if (file.exists() && (item.size <= 0 || file.length() == item.size)) return file
        return runCatching {
            withTimeout(300_000) { FirebaseStorage.getInstance().reference.child(item.path).getFile(file).await() }
            file
        }.getOrNull()
    }

    suspend fun saveLesson(lesson: Lesson, authorUid: String) {
        withTimeout(15_000) {
            if (lesson.id.isEmpty()) {
                lessons.add(
                    mapOf(
                        "title" to lesson.title, "passage" to lesson.passage, "body" to lesson.body,
                        "folderId" to lesson.folderId, "createdAt" to Timestamp.now(), "authorId" to authorUid
                    )
                ).await()
            } else {
                lessons.document(lesson.id).update(
                    mapOf("title" to lesson.title, "passage" to lesson.passage, "body" to lesson.body)
                ).await()
            }
        }
    }

    suspend fun deleteLesson(lesson: Lesson) {
        withTimeout(15_000) { lessons.document(lesson.id).delete().await() }
    }
}

data class WordState(
    val links: List<BibleLink> = emptyList(),
    val pdfs: List<BiblePdf> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
    val items: List<LibraryItem> = emptyList(),
    val loading: Boolean = true
)

class WordViewModel : ViewModel() {
    private val repo = WordRepository()
    private val _state = MutableStateFlow(WordState())
    val state: StateFlow<WordState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeLinks().collect { r -> r.onSuccess { l -> _state.update { it.copy(links = l, loading = false) } }.onFailure { _state.update { s -> s.copy(loading = false) } } }
        }
        viewModelScope.launch {
            repo.observePdfs().collect { r -> r.onSuccess { l -> _state.update { it.copy(pdfs = l) } } }
        }
        viewModelScope.launch {
            repo.observeFolders().collect { r -> r.onSuccess { l -> _state.update { it.copy(folders = l) } } }
        }
        viewModelScope.launch {
            repo.observeItems().collect { r -> r.onSuccess { l -> _state.update { it.copy(items = l) } } }
        }
        viewModelScope.launch {
            repo.observeLessons().collect { r -> r.onSuccess { l -> _state.update { it.copy(lessons = l) } } }
        }
    }

    suspend fun saveLink(link: BibleLink, uid: String): Result<Unit> = runCatching { repo.saveLink(link, uid) }
    fun deleteLink(link: BibleLink) { viewModelScope.launch { runCatching { repo.deleteLink(link) } } }

    suspend fun uploadPdf(context: Context, uri: Uri, title: String, uid: String): Result<Unit> =
        runCatching { repo.uploadPdf(context.applicationContext, uri, title, uid) }
    fun deletePdf(pdf: BiblePdf) { viewModelScope.launch { runCatching { repo.deletePdf(pdf) } } }
    suspend fun downloadPdf(context: Context, pdf: BiblePdf): File? = repo.downloadPdf(context.applicationContext, pdf)

    suspend fun createFolder(name: String, uid: String): Result<Unit> = runCatching { repo.createFolder(name, uid) }
    fun renameFolder(folder: LibraryFolder, name: String) { viewModelScope.launch { runCatching { repo.renameFolder(folder, name) } } }
    fun renameItem(item: LibraryItem, title: String) { viewModelScope.launch { runCatching { repo.renameItem(item, title) } } }
    fun deleteItem(item: LibraryItem) { viewModelScope.launch { runCatching { repo.deleteItem(item) } } }
    fun deleteFolder(folder: LibraryFolder, inside: List<LibraryItem>, insideLessons: List<Lesson> = emptyList()) {
        viewModelScope.launch { runCatching { repo.deleteFolder(folder, inside, insideLessons) } }
    }
    suspend fun uploadItem(context: Context, uri: Uri, folderId: String, uid: String): Result<Unit> =
        runCatching { repo.uploadItem(context.applicationContext, uri, folderId, uid) }
    suspend fun uploadVoice(file: File, durationMs: Int, title: String, folderId: String, uid: String): Result<Unit> =
        runCatching { repo.uploadVoice(file, durationMs, title, folderId, uid) }
    suspend fun downloadItem(context: Context, item: LibraryItem): File? = repo.downloadItem(context.applicationContext, item)

    suspend fun saveLesson(lesson: Lesson, uid: String): Result<Unit> = runCatching { repo.saveLesson(lesson, uid) }
    fun deleteLesson(lesson: Lesson) { viewModelScope.launch { runCatching { repo.deleteLesson(lesson) } } }
}
