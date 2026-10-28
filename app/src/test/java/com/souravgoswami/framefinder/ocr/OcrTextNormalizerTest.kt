package com.souravgoswami.framefinder.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextNormalizerTest {
    @Test
    fun collapsesInlineWhitespaceAndExcessBlankLines() {
        assertEquals("A receipt\n\nTotal 200", OcrTextNormalizer.normalize("  A\t receipt\n\n\n\nTotal   200  "))
    }

    @Test
    fun limitsStoredText() {
        assertTrue(OcrTextNormalizer.normalize("x".repeat(40_000)).length == 32_000)
    }
}
