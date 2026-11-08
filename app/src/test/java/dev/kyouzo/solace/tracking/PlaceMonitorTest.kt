package dev.kyouzo.solace.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceMonitorTest {
    private val radius = 20f

    @Test fun insideWhenWithinRadius() = assertEquals(true, PlaceMonitor.decide(distanceMeters = 12f, radiusMeters = radius, accuracyMeters = 8f, previous = null))

    @Test fun outsideOnlyWhenClearlyBeyondTheEdge() {
        // radius 20 + max(accuracy 8, 10 m minimum) = 30 m needed before we believe they left
        assertEquals(false, PlaceMonitor.decide(31f, radius, 8f, previous = true))
    }

    @Test fun edgeZoneKeepsThePreviousState() {
        assertEquals(true, PlaceMonitor.decide(25f, radius, 8f, previous = true))
        assertEquals(false, PlaceMonitor.decide(25f, radius, 8f, previous = false))
    }

    @Test fun edgeZoneWithNoHistoryStaysUndecided() = assertNull(PlaceMonitor.decide(25f, radius, 8f, previous = null))

    @Test fun poorAccuracyNeverChangesState() {
        assertEquals(true, PlaceMonitor.decide(500f, radius, 80f, previous = true))
        assertNull(PlaceMonitor.decide(1f, radius, 80f, previous = null))
    }

    @Test fun lowAccuracyWidensTheLeaveMargin() {
        // accuracy 30 m: must be beyond 20 + 30 = 50 m
        assertEquals(true, PlaceMonitor.decide(45f, radius, 30f, previous = true))
        assertEquals(false, PlaceMonitor.decide(55f, radius, 30f, previous = true))
    }
}
