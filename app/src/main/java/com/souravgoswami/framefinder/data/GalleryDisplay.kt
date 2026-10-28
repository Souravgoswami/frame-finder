package com.souravgoswami.framefinder.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PhotoLayout { GRID, LIST }
enum class ThumbnailDensity { COMPACT, COMFORTABLE, LARGE }
enum class GalleryGrouping { NONE, DAY, MONTH, FOLDER }

sealed interface GalleryItem {
    data class Header(val key: String, val label: String) : GalleryItem
    data class Photo(val photo: PhotoGridItem) : GalleryItem
}

internal data class GalleryGroup(val key: String, val label: String)

internal fun groupedGalleryItems(
    photos: List<PhotoGridItem>,
    grouping: GalleryGrouping,
    zoneId: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zoneId)
): List<GalleryItem> {
    if (grouping == GalleryGrouping.NONE) return photos.map(GalleryItem::Photo)

    return photos
        .groupBy { photo -> galleryGroup(photo, grouping, zoneId, today)!! }
        .flatMap { (group, groupedPhotos) ->
            listOf<GalleryItem>(GalleryItem.Header(group.key, group.label)) +
                groupedPhotos.map(GalleryItem::Photo)
        }
}

internal fun galleryGroup(
    photo: PhotoGridItem,
    grouping: GalleryGrouping,
    zoneId: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zoneId)
): GalleryGroup? = when (grouping) {
    GalleryGrouping.NONE -> null
    GalleryGrouping.FOLDER -> {
        val folder = photo.storagePath?.takeIf(String::isNotBlank) ?: "Unknown folder"
        GalleryGroup("folder:$folder", folder)
    }
    GalleryGrouping.DAY -> {
        val date = Instant.ofEpochMilli(photo.sortTime).atZone(zoneId).toLocalDate()
        val label = when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> DAY_FORMAT.format(date)
        }
        GalleryGroup("day:$date", label)
    }
    GalleryGrouping.MONTH -> {
        val month = Instant.ofEpochMilli(photo.sortTime).atZone(zoneId).toLocalDate().withDayOfMonth(1)
        GalleryGroup("month:$month", MONTH_FORMAT.format(month))
    }
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
