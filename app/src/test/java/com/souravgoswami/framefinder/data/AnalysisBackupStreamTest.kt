package com.souravgoswami.framefinder.data

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import com.souravgoswami.framefinder.ai.SearchModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class AnalysisBackupStreamTest {
    @Test
    fun readsSelectedModelFromManifest() {
        val manifest = parseBackupManifest(
            "format=1\nselectedModel=${SearchModel.MOBILECLIP_B.id}\ncontainsOriginalPhotos=false\n"
        )

        assertEquals(SearchModel.MOBILECLIP_B.id, manifest["selectedModel"])
    }

    @Test
    fun infersSingleSupportedModelForOlderBackup() {
        assertEquals(
            SearchModel.MOBILECLIP_B,
            inferBackupModel(setOf(SearchModel.MOBILECLIP_B.id, "retired-model"))
        )
        assertNull(
            inferBackupModel(setOf(SearchModel.MOBILECLIP_B.id, SearchModel.MOBILECLIP_S2.id))
        )
    }

    @Test
    fun readsLongAtRecordBoundary() {
        val bytes = java.io.ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { it.writeLong(-9_223_372_036_854_775_000L) }
        }.toByteArray()

        val input = DataInputStream(ByteArrayInputStream(bytes))
        assertEquals(-9_223_372_036_854_775_000L, input.readLongOrNull())
        assertNull(input.readLongOrNull())
    }

    @Test
    fun rejectsEndOfStreamInsideLong() {
        val input = DataInputStream(ByteArrayInputStream(byteArrayOf(0, 0, 0, 1)))
        try {
            input.readLongOrNull()
            fail("Expected a partial record to fail")
        } catch (_: EOFException) {
            // Expected.
        }
    }
}
