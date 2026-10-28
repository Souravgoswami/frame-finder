package com.souravgoswami.framefinder.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VectorCodec {
    fun encode(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % Float.SIZE_BYTES == 0) { "Invalid vector byte count" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
    }

    fun normalized(values: FloatArray): FloatArray {
        var squared = 0.0
        values.forEach { squared += it * it }
        val norm = sqrt(squared).toFloat()
        if (norm <= 0f || !norm.isFinite()) return values
        return FloatArray(values.size) { values[it] / norm }
    }

    fun cosineNormalized(left: FloatArray, rightBytes: ByteArray): Float {
        if (rightBytes.size != left.size * Float.SIZE_BYTES) return -1f
        val right = ByteBuffer.wrap(rightBytes).order(ByteOrder.LITTLE_ENDIAN)
        var score = 0f
        for (value in left) score += value * right.float
        return score
    }
}
