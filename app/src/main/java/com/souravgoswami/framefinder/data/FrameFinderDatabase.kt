package com.souravgoswami.framefinder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PhotoEntity::class, PhotoEmbeddingEntity::class], version = 9, exportSchema = true)
abstract class FrameFinderDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        fun create(context: Context): FrameFinderDatabase = Room.databaseBuilder(
            context.applicationContext,
            FrameFinderDatabase::class.java,
            "framefinder.db"
        ).addMigrations(*ALL_MIGRATIONS)
            .build()

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN ocrText TEXT")
                database.execSQL("ALTER TABLE photos ADD COLUMN ocrIndexedAt INTEGER")
                database.execSQL("ALTER TABLE photos ADD COLUMN ocrError TEXT")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN isDotHidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN embeddingModelId TEXT")
                database.execSQL("UPDATE photos SET embeddingModelId = 'mobileclip-s0' WHERE embedding IS NOT NULL")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN sortTime INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE photos SET sortTime = COALESCE(dateTaken, dateAdded)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_sortTime ON photos(sortTime)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_embeddingModelId ON photos(embeddingModelId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_ocrIndexedAt ON photos(ocrIndexedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_isDotHidden ON photos(isDotHidden)")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS photos_new (
                        mediaId INTEGER NOT NULL,
                        contentUri TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        mimeType TEXT,
                        dateTaken INTEGER,
                        dateAdded INTEGER NOT NULL,
                        dateModified INTEGER NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        width INTEGER,
                        height INTEGER,
                        storagePath TEXT,
                        description TEXT,
                        embedding BLOB,
                        embeddingModelId TEXT,
                        indexedAt INTEGER,
                        indexError TEXT,
                        ocrText TEXT,
                        ocrIndexedAt INTEGER,
                        ocrError TEXT,
                        isDotHidden INTEGER NOT NULL DEFAULT 0,
                        sortTime INTEGER NOT NULL DEFAULT 0,
                        lastSeenScan INTEGER NOT NULL,
                        PRIMARY KEY(mediaId)
                    )""".trimIndent()
                )
                database.execSQL(
                    """INSERT INTO photos_new (
                        mediaId, contentUri, displayName, mimeType, dateTaken, dateAdded,
                        dateModified, sizeBytes, width, height, storagePath, description,
                        embedding, embeddingModelId, indexedAt, indexError, ocrText,
                        ocrIndexedAt, ocrError, isDotHidden, sortTime, lastSeenScan
                    ) SELECT
                        mediaId, contentUri, displayName, mimeType, dateTaken, dateAdded,
                        dateModified, sizeBytes, width, height, NULL, description,
                        embedding, embeddingModelId, indexedAt, indexError, ocrText,
                        ocrIndexedAt, ocrError, isDotHidden, sortTime, lastSeenScan
                    FROM photos""".trimIndent()
                )
                database.execSQL("DROP TABLE photos")
                database.execSQL("ALTER TABLE photos_new RENAME TO photos")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_dateTaken ON photos(dateTaken)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_dateModified ON photos(dateModified)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_lastSeenScan ON photos(lastSeenScan)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_sortTime ON photos(sortTime)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_embeddingModelId ON photos(embeddingModelId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_ocrIndexedAt ON photos(ocrIndexedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_isDotHidden ON photos(isDotHidden)")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS photo_embeddings_backup (
                        mediaId INTEGER NOT NULL,
                        modelId TEXT NOT NULL,
                        embedding BLOB,
                        indexedAt INTEGER,
                        indexError TEXT,
                        PRIMARY KEY(mediaId, modelId)
                    )""".trimIndent()
                )
                database.execSQL(
                    """INSERT OR REPLACE INTO photo_embeddings_backup (
                        mediaId, modelId, embedding, indexedAt, indexError
                    ) SELECT mediaId, embeddingModelId, embedding, indexedAt, indexError
                    FROM photos
                    WHERE embeddingModelId IS NOT NULL""".trimIndent()
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS photos_new (
                        mediaId INTEGER NOT NULL,
                        contentUri TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        mimeType TEXT,
                        dateTaken INTEGER,
                        dateAdded INTEGER NOT NULL,
                        dateModified INTEGER NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        width INTEGER,
                        height INTEGER,
                        storagePath TEXT,
                        description TEXT,
                        ocrText TEXT,
                        ocrIndexedAt INTEGER,
                        ocrError TEXT,
                        isDotHidden INTEGER NOT NULL DEFAULT 0,
                        sortTime INTEGER NOT NULL DEFAULT 0,
                        lastSeenScan INTEGER NOT NULL,
                        PRIMARY KEY(mediaId)
                    )""".trimIndent()
                )
                database.execSQL(
                    """INSERT INTO photos_new (
                        mediaId, contentUri, displayName, mimeType, dateTaken, dateAdded,
                        dateModified, sizeBytes, width, height, storagePath, description,
                        ocrText, ocrIndexedAt, ocrError, isDotHidden, sortTime, lastSeenScan
                    ) SELECT
                        mediaId, contentUri, displayName, mimeType, dateTaken, dateAdded,
                        dateModified, sizeBytes, width, height, storagePath, description,
                        ocrText, ocrIndexedAt, ocrError, isDotHidden, sortTime, lastSeenScan
                    FROM photos""".trimIndent()
                )
                database.execSQL("DROP TABLE photos")
                database.execSQL("ALTER TABLE photos_new RENAME TO photos")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_dateTaken ON photos(dateTaken)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_dateModified ON photos(dateModified)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_lastSeenScan ON photos(lastSeenScan)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_sortTime ON photos(sortTime)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_ocrIndexedAt ON photos(ocrIndexedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_isDotHidden ON photos(isDotHidden)")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS photo_embeddings (
                        mediaId INTEGER NOT NULL,
                        modelId TEXT NOT NULL,
                        embedding BLOB,
                        indexedAt INTEGER,
                        indexError TEXT,
                        PRIMARY KEY(mediaId, modelId),
                        FOREIGN KEY(mediaId) REFERENCES photos(mediaId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                database.execSQL(
                    """INSERT INTO photo_embeddings (mediaId, modelId, embedding, indexedAt, indexError)
                    SELECT backup.mediaId, backup.modelId, backup.embedding, backup.indexedAt, backup.indexError
                    FROM photo_embeddings_backup AS backup
                    INNER JOIN photos ON photos.mediaId = backup.mediaId""".trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photo_embeddings_modelId ON photo_embeddings(modelId)")
                database.execSQL("DROP TABLE photo_embeddings_backup")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_isTrashed ON photos(isTrashed)")
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN isExcluded INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_photos_isExcluded ON photos(isExcluded)")
            }
        }

        internal val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9
        )
    }
}
