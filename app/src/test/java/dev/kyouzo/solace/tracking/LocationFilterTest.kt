package dev.kyouzo.solace.tracking

import dev.kyouzo.solace.data.local.LocationPointEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFilterTest {
    private fun point(lat: Double = 0.0, lon: Double = 0.0, accuracy: Float = 10f, at: Long = 0L) =
        LocationPointEntity("id", "u", "d", lat, lon, accuracy, null, null, null, null, at, null)

    private val max = TrackingPrecision.MAXIMUM
    private val balanced = TrackingPrecision.BALANCED

    @Test fun rejectsOutOfRangeCoordinates() {
        assertFalse(LocationFilter.accepts(91.0, 0.0, 10f, 0L, null))
        assertFalse(LocationFilter.accepts(0.0, 181.0, 10f, 0L, null))
    }

    @Test fun rejectsInvalidOrPoorAccuracy() {
        assertFalse(LocationFilter.accepts(1.0, 1.0, 0f, 0L, null))
        assertFalse(LocationFilter.accepts(1.0, 1.0, 36f, 0L, null, max))
        assertTrue(LocationFilter.accepts(1.0, 1.0, 35f, 0L, null, max))
        assertTrue(LocationFilter.accepts(1.0, 1.0, 90f, 0L, null, balanced))
    }

    @Test fun acceptsFirstGoodPoint() = assertTrue(LocationFilter.accepts(1.0, 1.0, 20f, 0L, null))

    @Test fun maximumRecordsAboutOneMetreOfMovement() {
        // 0.00001 deg latitude is about 1.1 m
        assertTrue(LocationFilter.accepts(0.00001, 0.0, 10f, 1_000L, point(), max))
    }

    @Test fun maximumIgnoresSubMetreJitter() {
        // 0.000005 deg is about 0.55 m
        assertFalse(LocationFilter.accepts(0.000005, 0.0, 10f, 1_000L, point(), max))
    }

    @Test fun balancedIgnoresSmallSteps() {
        assertFalse(LocationFilter.accepts(0.00001, 0.0, 10f, 10_000L, point(), balanced))
        // 0.00006 deg is about 6.7 m
        assertTrue(LocationFilter.accepts(0.00006, 0.0, 10f, 10_000L, point(), balanced))
    }

    @Test fun rejectsImpossibleSpeed() {
        // about 111 km in 10 s
        assertFalse(LocationFilter.accepts(1.0, 0.0, 10f, 10_000L, point(), max))
    }
}
