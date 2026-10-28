package com.souravgoswami.framefinder.work

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.souravgoswami.framefinder.ai.SearchModel
import com.souravgoswami.framefinder.appContainer
import com.souravgoswami.framefinder.data.PhotoEmbeddingEntity
import com.souravgoswami.framefinder.data.PhotoEntity
import com.souravgoswami.framefinder.data.PhotoMetadataUpdate
import com.souravgoswami.framefinder.data.VectorCodec
import com.souravgoswami.framefinder.media.MediaStoreScanner
import com.souravgoswami.framefinder.ocr.OnDeviceOcr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class PhotoIndexWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = applicationContext.appContainer()
        val model = SearchModel.fromId(inputData.getString(WorkContract.KEY_MODEL_ID))
        if (!container.modelManager.isReady(model)) {
            container.preferences.setScanStopped()
            return@withContext Result.failure(errorData("Bundled AI model is unavailable"))
        }
        val dao = container.database.photoDao()
        val forceRebuildRequested = inputData.getBoolean(WorkContract.KEY_FORCE_REBUILD, false)
        val scanner = MediaStoreScanner(applicationContext)
        val scanToken = System.currentTimeMillis()
        val preferences = container.preferences.read()
        val forceRebuild = forceRebuildRequested && preferences.scanForceRebuildPending
        val analyzeAll = forceRebuildRequested || inputData.getBoolean(WorkContract.KEY_ANALYZE_ALL, false)
        val ocr = OnDeviceOcr()

        try {
            publish(WorkContract.PHASE_PREPARE, 0, 0)
            container.modelManager.prepare(model)
            if (container.preferences.read().scanPaused) {
                publish(WorkContract.PHASE_PAUSING, 0, 0)
                return@withContext Result.success()
            }
            publish(WorkContract.PHASE_SCAN, 0, 0)
            val savedTimestamp = preferences.lastIndexedPhotoTimestamp
            val incrementalTimestamp = if (!analyzeAll && savedTimestamp <= 0L) {
                dao.newestIndexedSortTime().takeIf { it > 0L }?.also {
                    container.preferences.setLastIndexedPhotoTimestamp(it)
                }
            } else {
                savedTimestamp.takeIf { it > 0L }
            }
            val fullLibraryScan = analyzeAll || incrementalTimestamp == null
            val photos = scanner.discover(
                scanToken = scanToken,
                newerThan = if (fullLibraryScan) null else incrementalTimestamp,
                excludedFolders = preferences.excludedFolders
            )
            publish(WorkContract.PHASE_RESUME, 0, photos.size)
            val previousById = dao.getScanResumeStates(model.id).associateBy { it.mediaId }
            val newPhotos = ArrayList<PhotoEntity>()
            val metadataUpdates = ArrayList<PhotoMetadataUpdate>()
            val changedPhotoIds = ArrayList<Long>()
            val pending = ArrayList<PendingPhoto>()

            for (photo in photos) {
                val previous = previousById[photo.mediaId]
                val unchanged = previous != null &&
                    previous.dateModified == photo.dateModified &&
                    previous.sizeBytes == photo.sizeBytes
                val reusableEmbedding = isReusableForScan(
                    unchanged,
                    previous?.embeddingIndexedAt,
                    forceRebuild
                ) &&
                    previous?.hasEmbedding == true
                val reusableOcr = isReusableForScan(unchanged, previous?.ocrIndexedAt, forceRebuild)

                if (previous == null) {
                    newPhotos += photo
                } else {
                    metadataUpdates += photo.toMetadataUpdate()
                    if (!unchanged) changedPhotoIds += photo.mediaId
                }
                if (requiresPhotoWork(reusableEmbedding, reusableOcr)) {
                    pending += PendingPhoto(photo, reusableEmbedding, reusableOcr)
                }
            }

            newPhotos.forEachBatch { dao.upsertPhotos(it) }
            metadataUpdates.forEachBatch { dao.updatePhotoMetadata(it) }
            if (forceRebuild) {
                photos.map(PhotoEntity::mediaId).forEachBatch {
                    dao.deleteModelEmbeddingsForPhotos(model.id, it)
                    dao.clearOcrForPhotos(it)
                }
                // A resumed or retried worker must reuse work completed after this reset.
                container.preferences.setForceRebuildPrepared()
            } else {
                changedPhotoIds.forEachBatch {
                    dao.deleteEmbeddingsForPhotos(it)
                    dao.clearOcrForPhotos(it)
                }
            }
            if (fullLibraryScan) {
                // Reconcile removed MediaStore entries before the potentially long analysis pass.
                dao.deleteNotSeen(scanToken)
            }

            val alreadyCompleted = photos.size - pending.size
            var processedThisRun = 0
            val analysisStartedAt = SystemClock.elapsedRealtime()
            publish(WorkContract.PHASE_INDEX, alreadyCompleted, photos.size)

            if (shouldPauseBeforeNextPhoto(container.preferences.read().scanPaused, alreadyCompleted, photos.size)) {
                publish(WorkContract.PHASE_PAUSING, alreadyCompleted, photos.size)
                return@withContext Result.success()
            }

            for (item in pending) {
                coroutineContext.ensureActive()
                val photo = item.photo

                val needsOcr = !item.reusableOcr
                val needsEmbedding = !item.reusableEmbedding
                var sharedBitmap: android.graphics.Bitmap? = null
                var embeddingBitmap: android.graphics.Bitmap? = null

                try {
                    if (needsOcr) {
                        try {
                            sharedBitmap = scanner.loadOcrBitmap(photo)
                            val text = ocr.recognize(sharedBitmap)
                            dao.setOcrText(photo.mediaId, text, System.currentTimeMillis())
                        } catch (exception: Exception) {
                            coroutineContext.ensureActive()
                            dao.setOcrError(photo.mediaId, exception.message?.take(240) ?: "Unable to read text")
                        }
                    }

                    if (needsEmbedding) {
                        try {
                            val bitmap = sharedBitmap ?: scanner.loadInferenceBitmap(photo).also {
                                embeddingBitmap = it
                            }
                            val vector = container.embedder.encodeImage(bitmap, model)
                            dao.upsertEmbedding(
                                PhotoEmbeddingEntity(
                                    mediaId = photo.mediaId,
                                    modelId = model.id,
                                    embedding = VectorCodec.encode(vector),
                                    indexedAt = System.currentTimeMillis(),
                                    indexError = null
                                )
                            )
                        } catch (exception: Exception) {
                            coroutineContext.ensureActive()
                            dao.upsertEmbedding(
                                PhotoEmbeddingEntity(
                                    mediaId = photo.mediaId,
                                    modelId = model.id,
                                    embedding = null,
                                    indexedAt = null,
                                    indexError = exception.message?.take(240) ?: "Unable to analyze photo"
                                )
                            )
                        }
                    }
                } finally {
                    embeddingBitmap?.recycle()
                    sharedBitmap?.recycle()
                }
                processedThisRun++
                val completed = alreadyCompleted + processedThisRun
                if (processedThisRun == pending.size || processedThisRun % 3 == 0) {
                    publish(
                        WorkContract.PHASE_INDEX,
                        completed,
                        photos.size,
                        estimateRemainingMillis(
                            processedThisRun,
                            pending.size,
                            SystemClock.elapsedRealtime() - analysisStartedAt
                        )
                    )
                }
                if (shouldPauseBeforeNextPhoto(
                        container.preferences.read().scanPaused,
                        completed,
                        photos.size
                    )
                ) {
                    publish(WorkContract.PHASE_PAUSING, completed, photos.size)
                    return@withContext Result.success()
                }
            }
            publish(WorkContract.PHASE_CLEANUP, photos.size, photos.size)
            if (fullLibraryScan) {
                container.preferences.setStoragePathsIndexed()
            }
            val indexedThrough = maxOf(
                preferences.lastIndexedPhotoTimestamp,
                incrementalTimestamp ?: 0L,
                photos.maxOfOrNull { it.sortTime } ?: 0L
            )
            container.preferences.setScanCompleted(indexedThrough)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (security: SecurityException) {
            container.preferences.setScanStopped()
            Result.failure(errorData("Photo access was removed. Grant access and try again."))
        } catch (exception: Exception) {
            container.preferences.setScanStopped()
            Log.e(LOG_TAG, "Photo analysis failed", exception)
            Result.failure(errorData(userFacingIndexError(exception)))
        } finally {
            ocr.close()
        }
    }

    private suspend fun publish(phase: String, done: Int, total: Int, estimatedRemainingMillis: Long? = null) {
        setProgress(
            Data.Builder()
                .putString(WorkContract.KEY_PHASE, phase)
                .putInt(WorkContract.KEY_DONE, done)
                .putInt(WorkContract.KEY_TOTAL, total)
                .putLong(WorkContract.KEY_ESTIMATED_REMAINING_MS, estimatedRemainingMillis ?: -1L)
                .build()
        )
        setForeground(
            WorkerNotifications.foregroundInfo(
                applicationContext,
                7102,
                phase,
                done,
                total,
                estimatedRemainingMillis
            )
        )
    }

    private fun errorData(message: String) = Data.Builder()
        .putString(WorkContract.KEY_ERROR, message)
        .build()
}

internal fun isReusableForScan(
    unchanged: Boolean,
    completedAt: Long?,
    forceRebuild: Boolean = false
): Boolean = !forceRebuild && unchanged && completedAt != null

internal fun requiresPhotoWork(
    reusableEmbedding: Boolean,
    reusableOcr: Boolean
): Boolean = !reusableEmbedding || !reusableOcr

internal fun shouldPauseBeforeNextPhoto(pauseRequested: Boolean, completed: Int, total: Int): Boolean =
    pauseRequested && completed < total

internal fun userFacingIndexError(error: Throwable): String {
    val details = generateSequence(error as Throwable?) { it.cause }
        .take(MAX_ERROR_CAUSE_DEPTH)
        .mapNotNull(Throwable::message)
        .joinToString(" ")
    return if (
        details.contains("FOREIGN KEY constraint failed", ignoreCase = true) ||
        details.contains("SQLITE_CONSTRAINT_FOREIGNKEY", ignoreCase = true)
    ) {
        "One or more photos changed or were removed during analysis. " +
            "Your completed work is safe. Run analysis again to continue."
    } else {
        "Photo analysis stopped because of an unexpected problem. " +
            "Your completed work is safe. Try again."
    }
}

private data class PendingPhoto(
    val photo: PhotoEntity,
    val reusableEmbedding: Boolean,
    val reusableOcr: Boolean
)

private fun PhotoEntity.toMetadataUpdate() = PhotoMetadataUpdate(
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
    description = description,
    isDotHidden = isDotHidden,
    isTrashed = isTrashed,
    isExcluded = isExcluded,
    sortTime = sortTime,
    lastSeenScan = lastSeenScan
)

private suspend fun <T> List<T>.forEachBatch(block: suspend (List<T>) -> Unit) {
    chunked(DATABASE_BATCH_SIZE).forEach { block(it) }
}

private const val DATABASE_BATCH_SIZE = 500
private const val MAX_ERROR_CAUSE_DEPTH = 8
private const val LOG_TAG = "FrameFinderIndex"
