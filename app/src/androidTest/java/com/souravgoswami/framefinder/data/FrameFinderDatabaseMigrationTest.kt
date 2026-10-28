package com.souravgoswami.framefinder.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameFinderDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FrameFinderDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesVersionOneToLatestWithoutLosingAnalysis() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                """INSERT INTO photos (
                    mediaId, contentUri, displayName, mimeType, dateTaken, dateAdded,
                    dateModified, sizeBytes, width, height, description, embedding,
                    indexedAt, indexError, lastSeenScan
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                arrayOf(
                    42L,
                    "content://media/external/images/media/42",
                    "preserved.jpg",
                    "image/jpeg",
                    1_700_000_000_000L,
                    1_700_000_000_000L,
                    1_700_000_000L,
                    12_345L,
                    640,
                    480,
                    "Preserved description",
                    byteArrayOf(1, 2, 3, 4),
                    1_700_000_100_000L,
                    null,
                    77L
                )
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            9,
            true,
            *FrameFinderDatabase.ALL_MIGRATIONS
        )

        database.query(
            """SELECT displayName, description, storagePath, isDotHidden, isTrashed,
                isExcluded, sortTime, lastSeenScan FROM photos WHERE mediaId = 42""".trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("preserved.jpg", cursor.getString(0))
            assertEquals("Preserved description", cursor.getString(1))
            assertNull(cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals(0, cursor.getInt(4))
            assertEquals(0, cursor.getInt(5))
            assertEquals(1_700_000_000_000L, cursor.getLong(6))
            assertEquals(77L, cursor.getLong(7))
            assertFalse(cursor.moveToNext())
        }

        database.query(
            """SELECT modelId, embedding, indexedAt, indexError
                FROM photo_embeddings WHERE mediaId = 42""".trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("mobileclip-s0", cursor.getString(0))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), cursor.getBlob(1))
            assertEquals(1_700_000_100_000L, cursor.getLong(2))
            assertNull(cursor.getString(3))
            assertFalse(cursor.moveToNext())
        }
        database.close()
    }

    private companion object {
        const val DATABASE_NAME = "framefinder-migration-test"
    }
}
