package dev.kyouzo.solace.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryHelpersTest {
    @Test fun durationSplitsIntoUnits() {
        assertEquals(Triple(0L, 0L, 40L), durationParts(40_000))
        assertEquals(Triple(0L, 3L, 5L), durationParts(185_000))
        assertEquals(Triple(1L, 12L, 0L), durationParts(4_320_000))
    }

    @Test fun negativeDurationIsZero() = assertEquals(Triple(0L, 0L, 0L), durationParts(-5_000))

    @Test fun downsampleKeepsSmallListsUntouched() {
        val items = (1..10).toList()
        assertSame(items, downsample(items, 100))
    }

    @Test fun downsampleCapsSizeAndKeepsEnds() {
        val items = (1..10_000).toList()
        val sampled = downsample(items, 2_000)
        assertEquals(2_000, sampled.size)
        assertEquals(1, sampled.first())
        assertEquals(10_000, sampled.last())
        assertTrue("must stay in order", sampled.zipWithNext().all { (a, b) -> a < b })
    }
}
