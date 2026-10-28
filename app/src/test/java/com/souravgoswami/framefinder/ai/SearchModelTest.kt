package com.souravgoswami.framefinder.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchModelTest {
    @Test
    fun usesEachVisionModelsRequiredInputSize() {
        assertEquals(256, SearchModel.MOBILECLIP_S2.imageSize)
        assertEquals(224, SearchModel.MOBILECLIP_B.imageSize)
    }
}
