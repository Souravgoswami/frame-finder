package com.souravgoswami.framefinder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query(
        """SELECT mediaId, contentUri, displayName, mimeType, dateTaken, dateAdded, dateModified, sizeBytes,
            width, height, storagePath, sortTime
            FROM photos
            WHERE isTrashed = 0 AND isExcluded = 0 AND isDotHidden = 0
            ORDER BY
                CASE WHEN :groupMode = 'FOLDER' THEN storagePath IS NULL END ASC,
                CASE WHEN :groupMode = 'FOLDER' THEN storagePath END COLLATE NOCASE ASC,
                CASE WHEN (:groupMode = 'DAY' OR :groupMode = 'MONTH') AND :sortMode = 'OLDEST'
                    THEN sortTime END ASC,
                CASE WHEN (:groupMode = 'DAY' OR :groupMode = 'MONTH') AND :sortMode != 'OLDEST'
                    THEN sortTime END DESC,
                CASE WHEN (:groupMode = 'NONE' OR :groupMode = 'FOLDER') AND :sortMode = 'OLDEST'
                    THEN sortTime END ASC,
                CASE WHEN (:groupMode = 'NONE' OR :groupMode = 'FOLDER') AND :sortMode = 'NAME'
                    THEN displayName END COLLATE NOCASE ASC,
                CASE WHEN (:groupMode = 'NONE' OR :groupMode = 'FOLDER') AND :sortMode = 'LARGEST'
                    THEN sizeBytes END DESC,
                CASE WHEN (:groupMode = 'NONE' OR :groupMode = 'FOLDER') AND :sortMode = 'SMALLEST'
                    THEN sizeBytes END ASC,
                CASE WHEN (:groupMode = 'NONE' OR :groupMode = 'FOLDER')
                    AND (:sortMode = 'NEWEST' OR :sortMode = 'RELEVANCE') THEN sortTime END DESC,
                mediaId DESC
            LIMIT :limit OFFSET :offset"""
    )
    suspend fun getGalleryPage(
        sortMode: String,
        groupMode: String,
        limit: Int,
        offset: Int
    ): List<PhotoGridItem>

    @Query(
        """SELECT mediaId, contentUri, displayName, mimeType, dateTaken, dateAdded, dateModified, sizeBytes,
            width, height, storagePath, sortTime
            FROM photos
            WHERE isTrashed = 0 AND isExcluded = 0 AND isDotHidden = 0
            ORDER BY sortTime DESC, mediaId DESC
            LIMIT :limit OFFSET :offset"""
    )
    suspend fun getGalleryPageNewest(limit: Int, offset: Int): List<PhotoGridItem>

    @Query(
        """SELECT mediaId, contentUri, displayName, mimeType, dateTaken, dateAdded, dateModified, sizeBytes,
            width, height, storagePath, sortTime
            FROM photos
            WHERE isTrashed = 0 AND isExcluded = 0 AND isDotHidden = 0
            ORDER BY sortTime ASC, mediaId DESC
            LIMIT :limit OFFSET :offset"""
    )
    suspend fun getGalleryPageOldest(limit: Int, offset: Int): List<PhotoGridItem>

    @Query(
        """SELECT p.mediaId, p.contentUri, p.displayName, p.mimeType, p.dateTaken, p.dateAdded,
            p.dateModified, p.sizeBytes, p.width, p.height, p.storagePath, p.sortTime,
            p.description, e.embedding, e.modelId AS embeddingModelId, p.ocrText
            FROM photos AS p
            LEFT JOIN photo_embeddings AS e
                ON e.mediaId = p.mediaId AND e.modelId = :modelId
            WHERE p.mediaId > :afterMediaId
                AND p.isTrashed = 0 AND p.isExcluded = 0
                AND p.isDotHidden = 0
            ORDER BY p.mediaId
            LIMIT :limit"""
    )
    suspend fun getSearchPageAfter(
        modelId: String,
        afterMediaId: Long,
        limit: Int,
    ): List<PhotoSearchRow>

    @Query(
        """SELECT EXISTS(
            SELECT 1 FROM photo_embeddings AS e
            INNER JOIN photos AS p ON p.mediaId = e.mediaId
            WHERE p.isTrashed = 0 AND p.isExcluded = 0
                AND p.isDotHidden = 0
                AND e.embedding IS NOT NULL AND e.modelId = :modelId
            LIMIT 1
        )"""
    )
    suspend fun hasEmbedding(modelId: String): Boolean

    @Query(
        """SELECT mediaId, dateModified, sizeBytes, description, ocrText, ocrIndexedAt, ocrError
            FROM photos WHERE mediaId = :mediaId"""
    )
    suspend fun getIndexState(mediaId: Long): PhotoIndexState?

    @Query("SELECT mediaId, modelId, embedding, indexedAt, indexError FROM photo_embeddings WHERE mediaId = :mediaId AND modelId = :modelId")
    suspend fun getEmbeddingState(mediaId: Long, modelId: String): PhotoEmbeddingState?

    @Query(
        """SELECT p.mediaId, p.dateModified, p.sizeBytes, p.ocrIndexedAt,
            e.indexedAt AS embeddingIndexedAt,
            CASE WHEN e.embedding IS NOT NULL THEN 1 ELSE 0 END AS hasEmbedding
            FROM photos AS p
            LEFT JOIN photo_embeddings AS e
                ON e.mediaId = p.mediaId AND e.modelId = :modelId
            WHERE p.isTrashed = 0 AND p.isExcluded = 0 AND p.isDotHidden = 0"""
    )
    suspend fun getScanResumeStates(modelId: String): List<PhotoScanResumeState>

    @Query(
        """SELECT mediaId, dateModified, sizeBytes
            FROM photos
            WHERE isTrashed = 0 AND isExcluded = 0"""
    )
    suspend fun getPhotoSyncStates(): List<PhotoSyncState>

    @Query("SELECT COUNT(*) FROM photos WHERE isTrashed = 0 AND isExcluded = 0 AND isDotHidden = 0")
    fun observeCount(): Flow<Int>

    @Query(
        """SELECT COUNT(*) FROM photo_embeddings AS e
            INNER JOIN photos AS p ON p.mediaId = e.mediaId
            WHERE p.isTrashed = 0 AND p.isExcluded = 0
                AND p.isDotHidden = 0
                AND e.embedding IS NOT NULL AND e.modelId = :modelId"""
    )
    fun observeEmbeddedCount(modelId: String): Flow<Int>

    @Query(
        """SELECT COUNT(*) FROM photo_embeddings AS e
            INNER JOIN photos AS p ON p.mediaId = e.mediaId
            WHERE p.isTrashed = 0 AND p.isExcluded = 0
                AND p.isDotHidden = 0
                AND e.embedding IS NULL
                AND e.indexError IS NOT NULL AND e.modelId = :modelId"""
    )
    fun observeEmbeddingErrorCount(modelId: String): Flow<Int>

    @Query(
        """SELECT COUNT(*) FROM photos AS p
            WHERE p.isTrashed = 0 AND p.isExcluded = 0 AND p.isDotHidden = 0
                AND NOT EXISTS (
                    SELECT 1 FROM photo_embeddings AS completed
                    WHERE completed.mediaId = p.mediaId
                        AND completed.modelId = :modelId
                        AND completed.embedding IS NOT NULL
                )
                AND (
                    :ignoreFailures = 0 OR NOT EXISTS (
                        SELECT 1 FROM photo_embeddings AS failed
                        WHERE failed.mediaId = p.mediaId
                            AND failed.modelId = :modelId
                            AND failed.indexError IS NOT NULL
                    )
                )"""
    )
    fun observeMissingEmbeddingCount(modelId: String, ignoreFailures: Boolean): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE isTrashed = 0 AND isExcluded = 0 AND isDotHidden = 0 AND ocrIndexedAt IS NOT NULL")
    fun observeOcrCount(): Flow<Int>

    @Query(
        """SELECT COUNT(*) FROM photos
            WHERE isTrashed = 0 AND isExcluded = 0 AND isDotHidden = 0
                AND ocrIndexedAt IS NULL AND ocrError IS NOT NULL"""
    )
    fun observeOcrErrorCount(): Flow<Int>

    @Query(
        """SELECT COUNT(*) FROM photos
            WHERE isTrashed = 0 AND isExcluded = 0 AND isDotHidden = 0
                AND ocrIndexedAt IS NULL
                AND (:ignoreFailures = 0 OR ocrError IS NULL)"""
    )
    fun observeMissingOcrCount(ignoreFailures: Boolean): Flow<Int>

    @Query("SELECT COALESCE(MAX(sortTime), 0) FROM photos WHERE isTrashed = 0 AND isExcluded = 0 AND isDotHidden = 0")
    suspend fun newestIndexedSortTime(): Long

    @Upsert
    suspend fun upsert(photo: PhotoEntity)

    @Upsert
    suspend fun upsertPhotos(photos: List<PhotoEntity>)

    @Update(entity = PhotoEntity::class)
    suspend fun updatePhotoMetadata(photos: List<PhotoMetadataUpdate>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbedding(embedding: PhotoEmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbeddings(embeddings: List<PhotoEmbeddingEntity>)

    @Query("SELECT COUNT(*) FROM photos WHERE isExcluded = 0 AND isDotHidden = 0")
    suspend fun backupPhotoCount(): Int

    @Query(
        """SELECT COUNT(*) FROM photo_embeddings AS e
            INNER JOIN photos AS p ON p.mediaId = e.mediaId
            WHERE p.isExcluded = 0 AND p.isDotHidden = 0"""
    )
    suspend fun backupEmbeddingCount(): Int

    @Query("SELECT * FROM photos WHERE isExcluded = 0 AND isDotHidden = 0 ORDER BY mediaId LIMIT :limit OFFSET :offset")
    suspend fun getBackupPhotoPage(limit: Int, offset: Int): List<PhotoEntity>

    @Query(
        """SELECT e.* FROM photo_embeddings AS e
            INNER JOIN photos AS p ON p.mediaId = e.mediaId
            WHERE p.isExcluded = 0 AND p.isDotHidden = 0
            ORDER BY e.mediaId, e.modelId LIMIT :limit OFFSET :offset"""
    )
    suspend fun getBackupEmbeddingPage(limit: Int, offset: Int): List<PhotoEmbeddingEntity>

    @Query("DELETE FROM photo_embeddings WHERE mediaId = :mediaId")
    suspend fun deleteEmbeddingsForPhoto(mediaId: Long)

    @Query("DELETE FROM photo_embeddings WHERE mediaId IN (:mediaIds)")
    suspend fun deleteEmbeddingsForPhotos(mediaIds: List<Long>)

    @Query("DELETE FROM photo_embeddings WHERE modelId = :modelId AND mediaId IN (:mediaIds)")
    suspend fun deleteModelEmbeddingsForPhotos(modelId: String, mediaIds: List<Long>)

    @Query(
        """UPDATE photos
            SET ocrText = NULL, ocrIndexedAt = NULL, ocrError = NULL
            WHERE mediaId IN (:mediaIds)"""
    )
    suspend fun clearOcrForPhotos(mediaIds: List<Long>)

    @Query("UPDATE photos SET ocrText = :text, ocrIndexedAt = :indexedAt, ocrError = NULL WHERE mediaId = :mediaId")
    suspend fun setOcrText(mediaId: Long, text: String, indexedAt: Long)

    @Query("UPDATE photos SET ocrError = :message WHERE mediaId = :mediaId")
    suspend fun setOcrError(mediaId: Long, message: String)

    @Query("DELETE FROM photos WHERE isTrashed = 0 AND isExcluded = 0 AND lastSeenScan != :scanToken")
    suspend fun deleteNotSeen(scanToken: Long): Int

    @Query("UPDATE photos SET isTrashed = :trashed WHERE mediaId IN (:mediaIds)")
    suspend fun setTrashed(mediaIds: List<Long>, trashed: Boolean): Int

    @Query("DELETE FROM photos WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long): Int

    @Query("DELETE FROM photos WHERE mediaId IN (:mediaIds)")
    suspend fun deleteByMediaIds(mediaIds: List<Long>): Int

    @Query("SELECT mediaId, storagePath FROM photos WHERE isExcluded = 1")
    suspend fun getExcludedPhotoStates(): List<PhotoExclusionState>

    @Query("UPDATE photos SET isExcluded = 0 WHERE mediaId IN (:mediaIds)")
    suspend fun includePhotos(mediaIds: List<Long>): Int

    @Query(
        """UPDATE photos SET isExcluded = 1
            WHERE isExcluded = 0 AND (
                storagePath = :folder
                OR substr(storagePath, 1, length(:folder) + 1) = :folder || '/'
            )"""
    )
    suspend fun excludeStorageFolder(folder: String): Int

    @Transaction
    suspend fun syncExcludedFolders(folders: Collection<String>) {
        val normalizedFolders = folders.mapNotNull(ExcludedFolders::normalize).toSet()
        getExcludedPhotoStates()
            .filterNot { ExcludedFolders.contains(it.storagePath, normalizedFolders) }
            .map(PhotoExclusionState::mediaId)
            .chunked(DATABASE_UPDATE_BATCH_SIZE)
            .forEach { includePhotos(it) }
        normalizedFolders.forEach { excludeStorageFolder(it) }
    }

    @Query("DELETE FROM photos")
    suspend fun clear()

    companion object {
        private const val DATABASE_UPDATE_BATCH_SIZE = 500
    }
}
