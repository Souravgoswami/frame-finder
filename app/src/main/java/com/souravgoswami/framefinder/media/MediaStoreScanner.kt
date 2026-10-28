package com.souravgoswami.framefinder.media

import android.content.ContentUris
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import com.souravgoswami.framefinder.data.ExcludedFolders
import com.souravgoswami.framefinder.data.PhotoEntity
import com.souravgoswami.framefinder.data.PhotoGridItem
import com.souravgoswami.framefinder.data.TrashedPhoto
import java.io.IOException
import java.io.File

class MediaStoreScanner(private val context: Context) {
    private val resolver = context.contentResolver

    fun photoCount(excludedFolders: Set<String> = emptySet()): Int =
        countMatchingPhotos(null, null, excludedFolders)

    fun hasPhotoNewerThan(timestamp: Long, excludedFolders: Set<String> = emptySet()): Boolean {
        val (selection, arguments) = newerThanSelection(timestamp)
        return countMatchingPhotos(selection, arguments, excludedFolders, stopAfterFirst = true) > 0
    }

    fun photoCountNewerThan(timestamp: Long, excludedFolders: Set<String> = emptySet()): Int {
        val (selection, arguments) = newerThanSelection(timestamp)
        return countMatchingPhotos(selection, arguments, excludedFolders)
    }

    fun existingMediaIds(mediaIds: Collection<Long>): Set<Long> {
        if (mediaIds.isEmpty()) return emptySet()
        val existing = HashSet<Long>(mediaIds.size)
        mediaIds.distinct().chunked(MEDIA_ID_QUERY_CHUNK_SIZE).forEach { chunk ->
            val placeholders = List(chunk.size) { "?" }.joinToString(",")
            val cursor = resolver.query(
                collection(),
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media._ID} IN ($placeholders)",
                chunk.map(Long::toString).toTypedArray(),
                null
            ) ?: throw IOException("Photo library could not be checked")
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (it.moveToNext()) existing += it.getLong(idColumn)
            }
        }
        return existing
    }

    fun discover(
        scanToken: Long,
        newerThan: Long? = null,
        excludedFolders: Set<String> = emptySet()
    ): List<PhotoEntity> {
        val collection = collection()
        val pathColumnName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.MediaColumns.DATA
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DESCRIPTION,
            pathColumnName
        )
        val result = ArrayList<PhotoEntity>()
        val dateFilter = newerThan?.let(::newerThanSelection)
        val cursor = resolver.query(
            collection,
            projection,
            dateFilter?.first,
            dateFilter?.second,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        ) ?: throw IOException("Photo library could not be read")
        cursor.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val descriptionColumn = cursor.getColumnIndex(MediaStore.Images.Media.DESCRIPTION)
            val pathColumn = cursor.getColumnIndex(pathColumnName)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                val displayName = cursor.getString(nameColumn) ?: "Photo $id"
                val mediaPath = if (pathColumn >= 0) cursor.stringOrNull(pathColumn) else null
                val storagePath = storageFolder(mediaPath)
                if (ExcludedFolders.contains(storagePath, excludedFolders)) continue
                if (DotPathVisibility.isHidden(displayName, mediaPath)) continue
                val dateTaken = cursor.longOrNull(takenColumn)?.takeIf { it > 0L }
                val dateAdded = (cursor.longOrNull(addedColumn) ?: 0L) * 1_000L
                result += PhotoEntity(
                    mediaId = id,
                    contentUri = uri.toString(),
                    displayName = displayName,
                    mimeType = cursor.stringOrNull(mimeColumn),
                    dateTaken = dateTaken,
                    dateAdded = dateAdded,
                    dateModified = cursor.longOrNull(modifiedColumn) ?: 0L,
                    sizeBytes = cursor.longOrNull(sizeColumn) ?: 0L,
                    width = cursor.intOrNull(widthColumn)?.takeIf { it > 0 },
                    height = cursor.intOrNull(heightColumn)?.takeIf { it > 0 },
                    storagePath = storagePath,
                    description = if (descriptionColumn >= 0) cursor.stringOrNull(descriptionColumn) else null,
                    ocrText = null,
                    ocrIndexedAt = null,
                    ocrError = null,
                    isDotHidden = false,
                    isTrashed = false,
                    isExcluded = false,
                    sortTime = dateTaken ?: dateAdded,
                    lastSeenScan = scanToken
                )
            }
        }
        return result
    }

    fun trashedPhotos(): List<TrashedPhoto> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val collection = collection()
        val pathColumnName = storagePathColumnName()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_EXPIRES,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            pathColumnName
        )
        val queryArgs = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.MediaColumns.DATE_EXPIRES} ASC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
        }
        val result = ArrayList<TrashedPhoto>()
        resolver.query(collection, projection, queryArgs, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val expiresColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val pathColumn = cursor.getColumnIndex(pathColumnName)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateTaken = cursor.longOrNull(takenColumn)?.takeIf { it > 0L }
                val dateAdded = (cursor.longOrNull(addedColumn) ?: 0L) * 1_000L
                val mediaPath = if (pathColumn >= 0) cursor.stringOrNull(pathColumn) else null
                result += TrashedPhoto(
                    photo = PhotoGridItem(
                        mediaId = id,
                        contentUri = ContentUris.withAppendedId(collection, id).toString(),
                        displayName = cursor.stringOrNull(nameColumn) ?: "Photo $id",
                        mimeType = cursor.stringOrNull(mimeColumn),
                        dateTaken = dateTaken,
                        dateAdded = dateAdded,
                        dateModified = cursor.longOrNull(modifiedColumn) ?: 0L,
                        sizeBytes = cursor.longOrNull(sizeColumn) ?: 0L,
                        width = cursor.intOrNull(widthColumn)?.takeIf { it > 0 },
                        height = cursor.intOrNull(heightColumn)?.takeIf { it > 0 },
                        storagePath = storageFolder(mediaPath),
                        sortTime = dateTaken ?: dateAdded
                    ),
                    expiresAt = cursor.longOrNull(expiresColumn)?.takeIf { it > 0L }?.times(1_000L)
                )
            }
        }
        return result
    }

    fun storageFolders(): List<String> {
        val pathColumnName = storagePathColumnName()
        val folders = linkedSetOf<String>()
        resolver.query(
            collection(),
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME, pathColumnName),
            null,
            null,
            null
        )?.use { cursor ->
            val pathColumn = cursor.getColumnIndex(pathColumnName)
            while (cursor.moveToNext()) {
                val mediaPath = if (pathColumn >= 0) cursor.stringOrNull(pathColumn) else null
                storageFolder(mediaPath)?.let(folders::add)
            }
        }
        return folders.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    private fun collection() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private fun storagePathColumnName(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.MediaColumns.RELATIVE_PATH
    } else {
        @Suppress("DEPRECATION")
        MediaStore.MediaColumns.DATA
    }

    private fun countMatchingPhotos(
        selection: String?,
        arguments: Array<String>?,
        excludedFolders: Set<String>,
        stopAfterFirst: Boolean = false
    ): Int {
        val pathColumnName = storagePathColumnName()
        var count = 0
        resolver.query(
            collection(),
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME, pathColumnName),
            selection,
            arguments,
            null
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndex(pathColumnName)
            while (cursor.moveToNext()) {
                val displayName = cursor.stringOrNull(nameColumn).orEmpty()
                val mediaPath = if (pathColumn >= 0) cursor.stringOrNull(pathColumn) else null
                if (
                    !DotPathVisibility.isHidden(displayName, mediaPath) &&
                    !ExcludedFolders.contains(storageFolder(mediaPath), excludedFolders)
                ) {
                    count++
                    if (stopAfterFirst) return count
                }
            }
        }
        return count
    }

    private fun newerThanSelection(timestamp: Long): Pair<String, Array<String>> = Pair(
        "(${MediaStore.Images.Media.DATE_TAKEN} > ?) OR " +
            "((${MediaStore.Images.Media.DATE_TAKEN} IS NULL OR ${MediaStore.Images.Media.DATE_TAKEN} <= 0) " +
            "AND ${MediaStore.Images.Media.DATE_ADDED} > ?)",
        arrayOf(timestamp.toString(), (timestamp / 1_000L).toString())
    )

    fun loadInferenceBitmap(photo: PhotoEntity): Bitmap {
        return loadThumbnail(photo, 320, 640)
    }

    fun loadOcrBitmap(photo: PhotoEntity): Bitmap {
        return loadThumbnail(photo, 1_600, 1_600)
    }

    private fun loadThumbnail(photo: PhotoEntity, requestedSize: Int, legacyMaximum: Int): Bitmap {
        val uri = photo.uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return resolver.loadThumbnail(uri, Size(requestedSize, requestedSize), null)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Photo could not be decoded")
        var sample = 1
        while (bounds.outWidth / sample > legacyMaximum || bounds.outHeight / sample > legacyMaximum) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Photo could not be decoded")
    }

    private fun storageFolder(mediaPath: String?): String? = mediaPath
        ?.takeIf(String::isNotBlank)
        ?.let { path ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                path.trim('/', '\\').takeIf(String::isNotBlank)
            } else {
                File(path).parent?.takeIf(String::isNotBlank)
            }
        }
        ?.let(ExcludedFolders::normalize)

    private fun android.database.Cursor.longOrNull(column: Int): Long? =
        if (isNull(column)) null else getLong(column)

    private fun android.database.Cursor.intOrNull(column: Int): Int? =
        if (isNull(column)) null else getInt(column)

    private fun android.database.Cursor.stringOrNull(column: Int): String? =
        if (isNull(column)) null else getString(column)

    private companion object {
        const val MEDIA_ID_QUERY_CHUNK_SIZE = 400
    }
}

object DotPathVisibility {
    fun isHidden(displayName: String, mediaPath: String?): Boolean {
        return isDotItem(displayName) || mediaPath.orEmpty()
            .split('/', '\\')
            .any(::isDotItem)
    }

    private fun isDotItem(segment: String): Boolean =
        segment.length > 1 && segment.startsWith('.') && segment != ".."
}
