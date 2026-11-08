package dev.kyouzo.solace.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DayBoundsTest {
    private val noon = Calendar.getInstance().apply { set(2026, Calendar.JUNE, 15, 12, 30, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun containsTheInputInstant() {
        val (start, end) = dayBounds(noon)
        assertTrue(noon in start..end)
    }

    @Test fun startsAtLocalMidnightAndSpansOneDay() {
        val (start, end) = dayBounds(noon)
        val calendar = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        val hours = (end - start + 1) / 3_600_000.0
        assertTrue("day length was $hours h", hours in 23.0..25.0) // DST days are 23 or 25 hours
    }

    @Test fun adjacentDaysDoNotOverlap() {
        val (_, end) = dayBounds(noon)
        val (nextStart, _) = dayBounds(end + 1)
        assertEquals(end + 1, nextStart)
    }
}
