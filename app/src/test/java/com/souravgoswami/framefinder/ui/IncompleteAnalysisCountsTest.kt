package com.souravgoswami.framefinder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class IncompleteAnalysisCountsTest {
    @Test
    fun exactPendingCountsAreUsedForNotifications() {
        val result = incompleteAnalysisCounts(
            missingEmbeddingCount = 3,
            embeddingErrorCount = 3,
            missingOcrCount = 7,
            ocrErrorCount = 7
        )

        assertEquals(3, result.missingAiCount)
        assertEquals(7, result.missingTextCount)
        assertEquals(10, result.knownFailureCount)
    }

    @Test
    fun databaseFilteredFailuresDoNotCreateIncompleteCounts() {
        val result = incompleteAnalysisCounts(
            missingEmbeddingCount = 0,
            embeddingErrorCount = 3,
            missingOcrCount = 0,
            ocrErrorCount = 7
        )

        assertEquals(0, result.missingAiCount)
        assertEquals(0, result.missingTextCount)
        assertEquals(10, result.knownFailureCount)
    }

    @Test
    fun unprocessedPhotosStillCreateNotifications() {
        val result = incompleteAnalysisCounts(
            missingEmbeddingCount = 2,
            embeddingErrorCount = 3,
            missingOcrCount = 3,
            ocrErrorCount = 7
        )

        assertEquals(2, result.missingAiCount)
        assertEquals(3, result.missingTextCount)
    }

    @Test
    fun missingOcrAlwaysCreatesTextNotification() {
        val result = incompleteAnalysisCounts(
            missingEmbeddingCount = 0,
            embeddingErrorCount = 0,
            missingOcrCount = 100,
            ocrErrorCount = 0
        )

        assertEquals(100, result.missingTextCount)
    }

    @Test
    fun ignoredFailuresAreRemovedFromActionableMissingCount() {
        assertEquals(0, actionableMissingCount(3, 3, ignoreFailures = true))
        assertEquals(2, actionableMissingCount(5, 3, ignoreFailures = true))
        assertEquals(5, actionableMissingCount(5, 3, ignoreFailures = false))
    }
}
