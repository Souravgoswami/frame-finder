package com.souravgoswami.framefinder.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotPathVisibilityTest {
    @Test
    fun recognizesDotPrefixedFilename() {
        assertTrue(DotPathVisibility.isHidden(".preview.jpg", "Pictures/Camera/"))
    }

    @Test
    fun recognizesDotFolderInRelativeAndLegacyPaths() {
        assertTrue(DotPathVisibility.isHidden("photo.jpg", "Pictures/.private/Trips/"))
        assertTrue(DotPathVisibility.isHidden("photo.jpg", "/storage/emulated/0/.vault/photo.jpg"))
    }

    @Test
    fun ordinaryDotsDoNotHideAnItem() {
        assertFalse(DotPathVisibility.isHidden("holiday.2026.jpg", "Pictures/Trips.2026/"))
        assertFalse(DotPathVisibility.isHidden("photo.jpg", null))
    }
}
