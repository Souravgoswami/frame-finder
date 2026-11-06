package com.souravgoswami.framefinder.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SearchQueryParserTest {
    @Test
    fun parsesMonthAndYear() {
        val parsed = SearchQueryParser.parse("Pictures from October 2025")
        assertEquals(2025, parsed.date?.year)
        assertEquals(10, parsed.date?.month)
        assertEquals(listOf("pictures", "from", "october", "2025"), parsed.literalTerms)
        assertEquals(emptyList<String>(), parsed.terms)
    }

    @Test
    fun removesConfiguredWordsFromOnlyTheNonOcrQuery() {
        val original = SearchQueryParser.parse("Photo of a sleeping cat")
        val filtered = original.withoutWords(setOf("photo", "sleeping"))

        assertEquals(listOf("photo", "of", "a", "sleeping", "cat"), original.literalTerms)
        assertEquals(listOf("of", "a", "cat"), filtered.literalTerms)
        assertEquals("of a cat", filtered.normalized)
        assertEquals(listOf("cat"), filtered.terms)
    }

    @Test
    fun parsesCommaAndSpaceSeparatedIgnoredWords() {
        assertEquals(
            setOf("photo", "pictures", "index"),
            parseIgnoredSearchWords("Photo, pictures  INDEX")
        )
    }

    @Test
    fun dateConstraintUsesLocalCalendarDate() {
        val instant = LocalDateTime.of(2025, 10, 12, 9, 30)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val constraint = DateConstraint(2025, 10)
        assertEquals(true, constraint.matches(instant))
    }
}
