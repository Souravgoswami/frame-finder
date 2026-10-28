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
        assertEquals(emptyList<String>(), parsed.terms)
    }

    @Test
    fun dateConstraintUsesLocalCalendarDate() {
        val instant = LocalDateTime.of(2025, 10, 12, 9, 30)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val constraint = DateConstraint(2025, 10)
        assertEquals(true, constraint.matches(instant))
    }
}
