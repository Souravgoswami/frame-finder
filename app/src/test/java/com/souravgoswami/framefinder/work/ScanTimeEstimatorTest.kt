package com.souravgoswami.framefinder.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanTimeEstimatorTest {
    @Test
    fun scalesElapsedTimeByRemainingItems() {
        assertEquals(90_000L, estimateRemainingMillis(done = 10, total = 40, elapsedMillis = 30_000L))
    }

    @Test
    fun waitsForMeasuredProgress() {
        assertNull(estimateRemainingMillis(done = 0, total = 40, elapsedMillis = 1_000L))
        assertNull(estimateRemainingMillis(done = 40, total = 40, elapsedMillis = 10_000L))
    }

    @Test
    fun formatsUsefulDurations() {
        assertEquals("Less than a minute remaining", formatEstimatedRemaining(15_000L))
        assertEquals("About 2 minutes remaining", formatEstimatedRemaining(90_000L))
        assertEquals("About 1 h 30 min remaining", formatEstimatedRemaining(5_400_000L))
    }

    @Test
    fun formatsScanProgressWithPercentage() {
        assertEquals("250 of 1,000 photos, 25%", formatScanProgress(250, 1_000))
        assertEquals("1,000 of 1,000 photos, 100%", formatScanProgress(1_200, 1_000))
        assertEquals(null, formatScanProgress(0, 0))
    }
}
