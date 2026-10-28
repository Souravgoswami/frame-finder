package com.souravgoswami.framefinder.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VectorCodecTest {
    @Test
    fun roundTripsLittleEndianVector() {
        val values = floatArrayOf(-1.5f, 0f, 2.25f)
        assertArrayEquals(values, VectorCodec.decode(VectorCodec.encode(values)), 0f)
    }

    @Test
    fun normalizesAndScoresVector() {
        val normalized = VectorCodec.normalized(floatArrayOf(3f, 4f))
        assertEquals(0.6f, normalized[0], 0.0001f)
        assertEquals(1f, VectorCodec.cosineNormalized(normalized, VectorCodec.encode(normalized)), 0.0001f)
    }
}
