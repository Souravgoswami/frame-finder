package com.souravgoswami.framefinder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoDaoIntegrationTest {
    private lateinit var database: FrameFinderDatabase
    private lateinit var dao: PhotoDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FrameFinderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.photoDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun metadataRefreshPreservesCompletedAnalysis() = runBlocking {
        dao.upsert(photo(mediaId = 1L, ocrText = "receipt total", ocrIndexedAt = 200L))
        dao.upsertEmbedding(
            PhotoEmbeddingEntity(
                mediaId = 1L,
                modelId = "mobileclip-s2",
                embedding = byteArrayOf(1, 2, 3),
                indexedAt = 300L,
                indexError = null
            )
        )

        dao.updatePhotoMetadata(
            listOf(
                metadataUpdate(
                    mediaId = 1L,
                    displayName = "renamed.jpg",
                    lastSeenScan = 2L
                )
            )
        )

        val photoState = dao.getIndexState(1L)
        assertEquals("receipt total", photoState?.ocrText)
        assertEquals(200L, photoState?.ocrIndexedAt)
        val embedding = dao.getEmbeddingState(1L, "mobileclip-s2")
        assertNotNull(embedding?.embedding)
        assertEquals(300L, embedding?.indexedAt)
    }

    @Test
    fun failureCountsDistinguishFailedAndCompletedWork() = runBlocking {
        dao.upsert(photo(mediaId = 1L, ocrError = "Unreadable"))
        dao.upsert(photo(mediaId = 2L, ocrText = "done", ocrIndexedAt = 400L))
        dao.upsert(photo(mediaId = 3L))
        dao.upsertEmbedding(
            PhotoEmbeddingEntity(1L, "mobileclip-s2", null, null, "Decode failed")
        )
        dao.upsertEmbedding(
            PhotoEmbeddingEntity(2L, "mobileclip-s2", byteArrayOf(4, 5), 500L, null)
        )

        assertEquals(3, dao.observeCount().first())
        assertEquals(1, dao.observeOcrCount().first())
        assertEquals(1, dao.observeOcrErrorCount().first())
        assertEquals(1, dao.observeEmbeddedCount("mobileclip-s2").first())
        assertEquals(1, dao.observeEmbeddingErrorCount("mobileclip-s2").first())
        assertEquals(2, dao.observeMissingOcrCount(ignoreFailures = false).first())
        assertEquals(1, dao.observeMissingOcrCount(ignoreFailures = true).first())
        assertEquals(
            2,
            dao.observeMissingEmbeddingCount("mobileclip-s2", ignoreFailures = false).first()
        )
        assertEquals(
            1,
            dao.observeMissingEmbeddingCount("mobileclip-s2", ignoreFailures = true).first()
        )
    }

    @Test
    fun staleCleanupDeletesOnlyActiveMissingPhotosAndTheirEmbeddings() = runBlocking {
        dao.upsert(photo(mediaId = 1L, lastSeenScan = 2L))
        dao.upsert(photo(mediaId = 2L, lastSeenScan = 1L))
        dao.upsert(photo(mediaId = 3L, lastSeenScan = 1L, isTrashed = true))
        dao.upsert(photo(mediaId = 4L, lastSeenScan = 1L, isExcluded = true))
        dao.upsertEmbedding(
            PhotoEmbeddingEntity(2L, "mobileclip-s2", byteArrayOf(9), 600L, null)
        )

        assertEquals(1, dao.deleteNotSeen(scanToken = 2L))

        assertNotNull(dao.getIndexState(1L))
        assertNull(dao.getIndexState(2L))
        assertNotNull(dao.getIndexState(3L))
        assertNotNull(dao.getIndexState(4L))
        assertNull(dao.getEmbeddingState(2L, "mobileclip-s2"))
    }

    @Test
    fun dotItemsStayOutsideGallerySearchAndAnalysisCounts() = runBlocking {
        dao.upsert(photo(mediaId = 1L, isDotHidden = true))
        dao.upsertEmbedding(
            PhotoEmbeddingEntity(1L, "mobileclip-s2", byteArrayOf(1), 100L, null)
        )

        assertEquals(0, dao.observeCount().first())
        assertEquals(0, dao.observeEmbeddedCount("mobileclip-s2").first())
        assertEquals(0, dao.getGalleryPageNewest(limit = 20, offset = 0).size)
        assertEquals(
            0,
            dao.getSearchPageAfter("mobileclip-s2", afterMediaId = Long.MIN_VALUE, limit = 20).size
        )
    }

    private fun photo(
        mediaId: Long,
        lastSeenScan: Long = 1L,
        ocrText: String? = null,
        ocrIndexedAt: Long? = null,
        ocrError: String? = null,
        isDotHidden: Boolean = false,
        isTrashed: Boolean = false,
        isExcluded: Boolean = false
    ) = PhotoEntity(
        mediaId = mediaId,
        contentUri = "content://media/external/images/media/$mediaId",
        displayName = "$mediaId.jpg",
        mimeType = "image/jpeg",
        dateTaken = 1_000L,
        dateAdded = 1_000L,
        dateModified = 1L,
        sizeBytes = 100L,
        width = 10,
        height = 10,
        storagePath = "Pictures",
        description = null,
        ocrText = ocrText,
        ocrIndexedAt = ocrIndexedAt,
        ocrError = ocrError,
        isDotHidden = isDotHidden,
        isTrashed = isTrashed,
        isExcluded = isExcluded,
        sortTime = 1_000L,
        lastSeenScan = lastSeenScan
    )

    private fun metadataUpdate(
        mediaId: Long,
        displayName: String,
        lastSeenScan: Long
    ) = PhotoMetadataUpdate(
        mediaId = mediaId,
        contentUri = "content://media/external/images/media/$mediaId",
        displayName = displayName,
        mimeType = "image/jpeg",
        dateTaken = 1_000L,
        dateAdded = 1_000L,
        dateModified = 1L,
        sizeBytes = 100L,
        width = 10,
        height = 10,
        storagePath = "Pictures",
        description = null,
        isDotHidden = false,
        isTrashed = false,
        isExcluded = false,
        sortTime = 1_000L,
        lastSeenScan = lastSeenScan
    )
}
