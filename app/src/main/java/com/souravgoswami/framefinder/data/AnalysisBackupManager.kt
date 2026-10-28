package com.souravgoswami.framefinder.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.souravgoswami.framefinder.BuildConfig
import com.souravgoswami.framefinder.ai.SearchModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class AnalysisBackupSummary(
    val photos: Int,
    val embeddings: Int,
    val missingEmbeddings: Int = 0,
    val selectedModel: SearchModel? = null
)

class AnalysisBackupManager(
    private val context: Context,
    private val database: FrameFinderDatabase
) {
    private val dao = database.photoDao()

    suspend fun exportTo(uri: Uri, selectedModel: SearchModel): AnalysisBackupSummary = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("The selected backup file could not be opened.")
        var summary: AnalysisBackupSummary? = null
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            summary = database.withTransaction {
                // Keep counts and every paged query on the same database snapshot. Without
                // this transaction, a concurrent stale-photo cleanup could make the header
                // promise more records than the archive actually contains.
                val photoCount = dao.backupPhotoCount()
                val embeddingCount = dao.backupEmbeddingCount()
                val manifest = buildString {
                    appendLine("format=$FORMAT_VERSION")
                    appendLine("appVersion=${BuildConfig.VERSION_NAME}")
                    appendLine("createdAt=${System.currentTimeMillis()}")
                    appendLine("photos=$photoCount")
                    appendLine("embeddings=$embeddingCount")
                    appendLine("selectedModel=${selectedModel.id}")
                    appendLine("containsOriginalPhotos=false")
                }.toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(manifest)
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(DATA_ENTRY))
                val data = DataOutputStream(zip)
                data.writeInt(MAGIC)
                data.writeInt(FORMAT_VERSION)
                data.writeInt(photoCount)
                data.writeInt(embeddingCount)

                var offset = 0
                while (offset < photoCount) {
                    val page = dao.getBackupPhotoPage(PAGE_SIZE, offset)
                    if (page.isEmpty()) throw IOException("The photo index changed during export.")
                    page.forEach { photo -> data.writePhoto(photo) }
                    offset += page.size
                }
                offset = 0
                while (offset < embeddingCount) {
                    val page = dao.getBackupEmbeddingPage(PAGE_SIZE, offset)
                    if (page.isEmpty()) throw IOException("The AI index changed during export.")
                    page.forEach { embedding -> data.writeEmbedding(embedding) }
                    offset += page.size
                }
                data.flush()
                zip.closeEntry()
                AnalysisBackupSummary(photoCount, embeddingCount, selectedModel = selectedModel)
            }
        }
        summary ?: throw IOException("The backup could not be completed.")
    }

    suspend fun importFrom(uri: Uri): AnalysisBackupSummary = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("The selected backup file could not be opened.")
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var manifestModel: SearchModel? = null
            var entry = zip.nextEntry
            while (entry != null && entry.name != DATA_ENTRY) {
                if (entry.name == MANIFEST_ENTRY) {
                    val manifest = parseBackupManifest(zip.readBoundedManifest())
                    manifestModel = supportedModel(manifest[SELECTED_MODEL_KEY])
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            if (entry == null) throw IOException("This is not a Frame Finder analysis backup.")
            val data = DataInputStream(zip)
            val summary = database.withTransaction {
                if (data.readInt() != MAGIC) throw IOException("The backup header is invalid.")
                val version = data.readInt()
                if (version != FORMAT_VERSION) {
                    throw IOException("Backup format $version is not supported by this version of Frame Finder.")
                }
                val photoCount = data.readValidatedCount("photo")
                val expectedEmbeddingCount = data.readValidatedCount("AI result")

                val photos = ArrayList<PhotoEntity>(IMPORT_BATCH_SIZE)
                repeat(photoCount) {
                    photos += data.readPhoto()
                    if (photos.size == IMPORT_BATCH_SIZE) {
                        dao.upsertPhotos(photos)
                        photos.clear()
                    }
                }
                if (photos.isNotEmpty()) dao.upsertPhotos(photos)

                val embeddings = ArrayList<PhotoEmbeddingEntity>(IMPORT_BATCH_SIZE)
                val importedModelIds = mutableSetOf<String>()
                var importedEmbeddingCount = 0
                while (importedEmbeddingCount < expectedEmbeddingCount) {
                    val embedding = data.readEmbeddingOrNull() ?: break
                    embeddings += embedding
                    importedModelIds += embedding.modelId
                    importedEmbeddingCount++
                    if (embeddings.size == IMPORT_BATCH_SIZE) {
                        dao.upsertEmbeddings(embeddings)
                        embeddings.clear()
                    }
                }
                if (embeddings.isNotEmpty()) dao.upsertEmbeddings(embeddings)
                if (data.read() != -1) throw IOException("The backup contains unexpected trailing data.")
                AnalysisBackupSummary(
                    photos = photoCount,
                    embeddings = importedEmbeddingCount,
                    missingEmbeddings = expectedEmbeddingCount - importedEmbeddingCount,
                    selectedModel = manifestModel ?: inferBackupModel(importedModelIds)
                )
            }
            zip.closeEntry()
            summary
        }
    }

    private fun DataOutputStream.writePhoto(photo: PhotoEntity) {
        writeLong(photo.mediaId)
        writeSizedString(photo.contentUri)
        writeSizedString(photo.displayName)
        writeNullableString(photo.mimeType)
        writeNullableLong(photo.dateTaken)
        writeLong(photo.dateAdded)
        writeLong(photo.dateModified)
        writeLong(photo.sizeBytes)
        writeNullableInt(photo.width)
        writeNullableInt(photo.height)
        writeNullableString(photo.storagePath)
        writeNullableString(photo.description)
        writeNullableString(photo.ocrText)
        writeNullableLong(photo.ocrIndexedAt)
        writeNullableString(photo.ocrError)
        writeBoolean(photo.isDotHidden)
        writeBoolean(photo.isTrashed)
        writeLong(photo.sortTime)
        writeLong(photo.lastSeenScan)
    }

    private fun DataInputStream.readPhoto() = PhotoEntity(
        mediaId = readLong(),
        contentUri = readSizedString(),
        displayName = readSizedString(),
        mimeType = readNullableString(),
        dateTaken = readNullableLong(),
        dateAdded = readLong(),
        dateModified = readLong(),
        sizeBytes = readLong(),
        width = readNullableInt(),
        height = readNullableInt(),
        storagePath = readNullableString(),
        description = readNullableString(),
        ocrText = readNullableString(),
        ocrIndexedAt = readNullableLong(),
        ocrError = readNullableString(),
        isDotHidden = readBoolean(),
        isTrashed = readBoolean(),
        isExcluded = false,
        sortTime = readLong(),
        lastSeenScan = readLong()
    )

    private fun DataOutputStream.writeEmbedding(embedding: PhotoEmbeddingEntity) {
        writeLong(embedding.mediaId)
        writeSizedString(embedding.modelId)
        writeNullableBytes(embedding.embedding)
        writeNullableLong(embedding.indexedAt)
        writeNullableString(embedding.indexError)
    }

    private fun DataInputStream.readEmbeddingOrNull(): PhotoEmbeddingEntity? {
        val mediaId = readLongOrNull() ?: return null
        return PhotoEmbeddingEntity(
            mediaId = mediaId,
            modelId = readSizedString(),
            embedding = readNullableBytes(),
            indexedAt = readNullableLong(),
            indexError = readNullableString()
        )
    }

    private fun DataOutputStream.writeSizedString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FIELD_BYTES) { "A backup text field is too large." }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readSizedString(): String {
        val size = readBoundedSize(MAX_FIELD_BYTES, "text field")
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeSizedString(value)
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readSizedString() else null

    private fun DataOutputStream.writeNullableBytes(value: ByteArray?) {
        writeBoolean(value != null)
        if (value != null) {
            require(value.size <= MAX_FIELD_BYTES) { "A backup binary field is too large." }
            writeInt(value.size)
            write(value)
        }
    }

    private fun DataInputStream.readNullableBytes(): ByteArray? {
        if (!readBoolean()) return null
        val size = readBoundedSize(MAX_FIELD_BYTES, "binary field")
        return ByteArray(size).also(::readFully)
    }

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null

    private fun DataOutputStream.writeNullableInt(value: Int?) {
        writeBoolean(value != null)
        if (value != null) writeInt(value)
    }

    private fun DataInputStream.readNullableInt(): Int? = if (readBoolean()) readInt() else null

    private fun DataInputStream.readValidatedCount(label: String): Int {
        val count = readInt()
        if (count !in 0..MAX_RECORDS) throw IOException("The backup has an invalid $label count.")
        return count
    }

    private fun DataInputStream.readBoundedSize(maximum: Int, label: String): Int {
        val size = readInt()
        if (size !in 0..maximum) throw IOException("The backup has an invalid $label size.")
        return size
    }

    private fun ZipInputStream.readBoundedManifest(): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        while (true) {
            val count = read(buffer)
            if (count == -1) break
            if (output.size() + count > MAX_MANIFEST_BYTES) {
                throw IOException("The backup manifest is too large.")
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val MAGIC = 0x46464231
        const val FORMAT_VERSION = 1
        const val MANIFEST_ENTRY = "manifest.txt"
        const val DATA_ENTRY = "analysis.bin"
        const val SELECTED_MODEL_KEY = "selectedModel"
        const val PAGE_SIZE = 256
        const val IMPORT_BATCH_SIZE = 128
        const val MAX_RECORDS = 1_000_000
        const val MAX_FIELD_BYTES = 64 * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 64 * 1024
    }
}

internal fun parseBackupManifest(text: String): Map<String, String> = text
    .lineSequence()
    .mapNotNull { line ->
        val separator = line.indexOf('=')
        if (separator <= 0) null else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }
    .toMap()

private fun supportedModel(id: String?): SearchModel? =
    SearchModel.entries.firstOrNull { model -> model.id == id }

internal fun inferBackupModel(modelIds: Set<String>): SearchModel? = modelIds
    .mapNotNull(::supportedModel)
    .distinct()
    .singleOrNull()

/** Returns null only when the stream ends before a new record starts. */
internal fun DataInputStream.readLongOrNull(): Long? {
    val first = read()
    if (first == -1) return null
    var value = first.toLong()
    repeat(Long.SIZE_BYTES - 1) {
        val next = read()
        if (next == -1) throw EOFException("The backup ended partway through an AI record.")
        value = (value shl 8) or next.toLong()
    }
    return value
}
