package com.souravgoswami.framefinder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FastScrollbarTest {
    @Test
    fun listTargetIncludesSmoothWithinItemOffset() {
        assertEquals(
            FastScrollTarget(itemIndex = 50, scrollOffset = 100),
            fastScrollTarget(
                fraction = 0.505f,
                maxFirstItem = 100,
                lanes = 1,
                itemExtent = 200
            )
        )
    }

    @Test
    fun gridTargetIncludesSmoothWithinRowOffset() {
        assertEquals(
            FastScrollTarget(itemIndex = 50, scrollOffset = 100),
            fastScrollTarget(
                fraction = 0.525f,
                maxFirstItem = 100,
                lanes = 5,
                itemExtent = 200
            )
        )
    }

    @Test
    fun targetIsClampedAtBothEnds() {
        assertEquals(
            FastScrollTarget(itemIndex = 0, scrollOffset = 0),
            fastScrollTarget(-1f, maxFirstItem = 100, lanes = 5, itemExtent = 200)
        )
        assertEquals(
            FastScrollTarget(itemIndex = 100, scrollOffset = 0),
            fastScrollTarget(2f, maxFirstItem = 100, lanes = 5, itemExtent = 200)
        )
    }
}
