package com.souravgoswami.framefinder.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryDisplayTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 25)

    @Test
    fun createsFriendlyDayGroups() {
        assertEquals("Today", galleryGroup(photo("2026-10-25"), GalleryGrouping.DAY, zone, today)?.label)
        assertEquals("Yesterday", galleryGroup(photo("2026-10-24"), GalleryGrouping.DAY, zone, today)?.label)
        assertEquals("2 October 2026", galleryGroup(photo("2026-10-02"), GalleryGrouping.DAY, zone, today)?.label)
    }

    @Test
    fun createsMonthAndFolderGroups() {
        assertEquals("October 2026", galleryGroup(photo("2026-10-02"), GalleryGrouping.MONTH, zone, today)?.label)
        assertEquals("Pictures/Screenshots", galleryGroup(photo("2026-10-02"), GalleryGrouping.FOLDER, zone, today)?.label)
    }

    @Test
    fun returnsNoGroupWhenGroupingIsDisabled() {
        assertNull(galleryGroup(photo("2026-10-02"), GalleryGrouping.NONE, zone, today))
    }

    @Test
    fun groupsOrderedSearchResultsWithoutLosingRankWithinEachGroup() {
        val items = groupedGalleryItems(
            photos = listOf(
                photo("2026-10-24", id = 1L),
                photo("2026-10-25", id = 2L),
                photo("2026-10-24", id = 3L)
            ),
            grouping = GalleryGrouping.DAY,
            zoneId = zone,
            today = today
        )

        assertEquals(
            listOf("Yesterday", "1", "3", "Today", "2"),
            items.map { item ->
                when (item) {
                    is GalleryItem.Header -> item.label
                    is GalleryItem.Photo -> item.photo.mediaId.toString()
                }
            }
        )
    }

    private fun photo(date: String, id: Long = 1L) = PhotoGridItem(
        mediaId = id,
        contentUri = "content://photo/1",
        displayName = "photo.jpg",
        mimeType = "image/jpeg",
        dateTaken = null,
        dateAdded = 0L,
        dateModified = 0L,
        sizeBytes = 1L,
        width = 1,
        height = 1,
        storagePath = "Pictures/Screenshots",
        sortTime = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
    )
}
