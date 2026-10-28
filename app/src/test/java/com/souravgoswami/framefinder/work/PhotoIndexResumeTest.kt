package com.souravgoswami.framefinder.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoIndexResumeTest {
    @Test
    fun normalResumeReusesCompletedUnchangedWork() {
        assertTrue(isReusableForScan(true, 100L))
        assertFalse(isReusableForScan(false, 100L))
    }

    @Test
    fun analyzeAllReusesEveryFinishedUnchangedResult() {
        assertTrue(isReusableForScan(true, 999L))
        assertFalse(isReusableForScan(true, null))
    }

    @Test
    fun forcedRebuildDoesNotReuseFinishedWork() {
        assertFalse(isReusableForScan(true, 999L, forceRebuild = true))
    }

    @Test
    fun finishedPhotoIsExcludedFromTheResumedWorkQueue() {
        assertFalse(
            requiresPhotoWork(
                reusableEmbedding = true,
                reusableOcr = true
            )
        )
        assertTrue(
            requiresPhotoWork(
                reusableEmbedding = false,
                reusableOcr = true
            )
        )
        assertTrue(
            requiresPhotoWork(
                reusableEmbedding = true,
                reusableOcr = false
            )
        )
    }

    @Test
    fun missingOcrAlwaysCreatesWork() {
        assertTrue(
            requiresPhotoWork(
                reusableEmbedding = true,
                reusableOcr = false
            )
        )
    }

    @Test
    fun gracefulPauseWaitsForCurrentPhotoAndStopsBeforeTheNextOne() {
        assertTrue(shouldPauseBeforeNextPhoto(pauseRequested = true, completed = 3, total = 10))
        assertFalse(shouldPauseBeforeNextPhoto(pauseRequested = false, completed = 3, total = 10))
        assertFalse(shouldPauseBeforeNextPhoto(pauseRequested = true, completed = 10, total = 10))
    }

    @Test
    fun foreignKeyFailureHasARecoverableUserMessage() {
        val error = IllegalStateException(
            "Database write failed",
            RuntimeException("FOREIGN KEY constraint failed (code 787 SQLITE_CONSTRAINT_FOREIGNKEY)")
        )

        assertEquals(
            "One or more photos changed or were removed during analysis. " +
                "Your completed work is safe. Run analysis again to continue.",
            userFacingIndexError(error)
        )
    }

    @Test
    fun unexpectedFailureDoesNotExposeTechnicalDetails() {
        val message = userFacingIndexError(IllegalStateException("Secret database detail"))

        assertEquals(
            "Photo analysis stopped because of an unexpected problem. " +
                "Your completed work is safe. Try again.",
            message
        )
    }
}
