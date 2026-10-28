package com.souravgoswami.framefinder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedFoldersTest {
    @Test
    fun normalizesSeparatorsAndTrailingSlash() {
        assertEquals("Pictures/Screenshots", ExcludedFolders.normalize(" Pictures\\Screenshots/ "))
    }

    @Test
    fun matchesFolderAndItsChildren() {
        val excluded = setOf("Pictures")

        assertTrue(ExcludedFolders.contains("Pictures", excluded))
        assertTrue(ExcludedFolders.contains("Pictures/Screenshots", excluded))
        assertFalse(ExcludedFolders.contains("Pictures 2", excluded))
        assertFalse(ExcludedFolders.contains("DCIM/Pictures", excluded))
    }

    @Test
    fun ignoresMissingStoragePath() {
        assertFalse(ExcludedFolders.contains(null, setOf("Pictures")))
    }
}
