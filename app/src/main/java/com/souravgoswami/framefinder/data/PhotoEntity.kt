package com.souravgoswami.framefinder.data

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [
        Index("dateTaken"),
        Index("dateModified"),
        Index("lastSeenScan"),
        Index("sortTime"),
        Index("ocrIndexedAt"),
        Index("isDotHidden"),
        Index("isTrashed"),
        Index("isExcluded")
    ]
)
data class PhotoEntity(
    @PrimaryKey val mediaId: Long,
    val contentUri: String,
    val displayName: String,
    val mimeType: String?,
    val dateTaken: Long?,
    val dateAdded: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val storagePath: String?,
    val description: String?,
    val ocrText: String?,
    val ocrIndexedAt: Long?,
    val ocrError: String?,
    @ColumnInfo(defaultValue = "0") val isDotHidden: Boolean,
    @ColumnInfo(defaultValue = "0") val isTrashed: Boolean,
    @ColumnInfo(defaultValue = "0") val isExcluded: Boolean,
    @ColumnInfo(defaultValue = "0") val sortTime: Long,
    val lastSeenScan: Long
) {
    val uri: Uri get() = Uri.parse(contentUri)
}

@Entity(
    tableName = "photo_embeddings",
    primaryKeys = ["mediaId", "modelId"],
    indices = [Index("modelId")],
    foreignKeys = [
        ForeignKey(
            entity = PhotoEntity::class,
            parentColumns = ["mediaId"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PhotoEmbeddingEntity(
    val mediaId: Long,
    val modelId: String,
    val embedding: ByteArray?,
    val indexedAt: Long?,
    val indexError: String?
)

/** Lightweight projection used by the paged gallery and search results. */
data class PhotoGridItem(
    val mediaId: Long,
    val contentUri: String,
    val displayName: String,
    val mimeType: String?,
    val dateTaken: Long?,
    val dateAdded: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val storagePath: String?,
    val sortTime: Long
)

/** A bounded search-page row; error/status columns are intentionally excluded. */
data class PhotoSearchRow(
    val mediaId: Long,
    val contentUri: String,
    val displayName: String,
    val mimeType: String?,
    val dateTaken: Long?,
    val dateAdded: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val storagePath: String?,
    val sortTime: Long,
    val description: String?,
    val embedding: ByteArray?,
    val embeddingModelId: String?,
    val ocrText: String?
) {
    fun toGridItem() = PhotoGridItem(
        mediaId = mediaId,
        contentUri = contentUri,
        displayName = displayName,
        mimeType = mimeType,
        dateTaken = dateTaken,
        dateAdded = dateAdded,
        dateModified = dateModified,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        storagePath = storagePath,
        sortTime = sortTime
    )
}

data class PhotoIndexState(
    val mediaId: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val description: String?,
    val ocrText: String?,
    val ocrIndexedAt: Long?,
    val ocrError: String?
)

data class PhotoExclusionState(
    val mediaId: Long,
    val storagePath: String?
)

/** Small projection used to decide whether a full library scan can skip saved work. */
data class PhotoScanResumeState(
    val mediaId: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val ocrIndexedAt: Long?,
    val embeddingIndexedAt: Long?,
    val hasEmbedding: Boolean
)

/** Minimal state used to reconcile MediaStore without loading saved analysis. */
data class PhotoSyncState(
    val mediaId: Long,
    val dateModified: Long,
    val sizeBytes: Long
)

/** Partial Room update that refreshes MediaStore fields without replacing saved analysis. */
data class PhotoMetadataUpdate(
    val mediaId: Long,
    val contentUri: String,
    val displayName: String,
    val mimeType: String?,
    val dateTaken: Long?,
    val dateAdded: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val storagePath: String?,
    val description: String?,
    val isDotHidden: Boolean,
    val isTrashed: Boolean,
    val isExcluded: Boolean,
    val sortTime: Long,
    val lastSeenScan: Long
)

data class PhotoEmbeddingState(
    val mediaId: Long,
    val modelId: String,
    val embedding: ByteArray?,
    val indexedAt: Long?,
    val indexError: String?
)

data class TrashedPhoto(
    val photo: PhotoGridItem,
    val expiresAt: Long?
)
