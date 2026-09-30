package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lukabencina.kilometrina.data.AutomaticRouteDetector
import si.lukabencina.kilometrina.data.LocationPointEntity

class AutomaticRouteDetectorTest {
    @Test
    fun detectsMeaningfulDwellGap() {
        val points = listOf(
            point(1, 0, 46.0000, 14.0000),
            point(2, 60_000, 46.1000, 14.1000),
            point(3, 120_000, 46.5000, 14.5000),
            point(4, 420_000, 46.5002, 14.5001),
            point(5, 480_000, 46.3000, 14.3000),
            point(6, 540_000, 46.0000, 14.0000),
        )

        val result = AutomaticRouteDetector.detect(points)

        assertEquals(1, result.size)
        assertEquals(AutomaticRouteDetector.Source.DWELL, result.single().source)
        assertTrue(result.single().lat in 46.499..46.501)
    }

    @Test
    fun ignoresLongGapWhenVehicleMovedFarAway() {
        val points = listOf(
            point(1, 0, 46.0000, 14.0000),
            point(2, 60_000, 46.1000, 14.1000),
            point(3, 420_000, 46.5000, 14.5000),
            point(4, 480_000, 46.7000, 14.7000),
        )

        assertTrue(AutomaticRouteDetector.detect(points).isEmpty())
    }

    @Test
    fun roundTripFallsBackToFarthestTurnaround() {
        val points = listOf(
            point(1, 0, 46.0000, 14.0000),
            point(2, 60_000, 46.1000, 14.1000),
            point(3, 120_000, 46.5000, 14.5000),
            point(4, 180_000, 46.1000, 14.1000),
            point(5, 240_000, 46.0002, 14.0001),
        )

        val result = AutomaticRouteDetector.detect(points)

        assertEquals(1, result.size)
        assertEquals(AutomaticRouteDetector.Source.TURNAROUND, result.single().source)
        assertEquals(46.5, result.single().lat, 0.0001)
    }

    @Test
    fun oneWayTripDoesNotInventTurnaround() {
        val points = listOf(
            point(1, 0, 46.0000, 14.0000),
            point(2, 60_000, 46.2000, 14.2000),
            point(3, 120_000, 46.5000, 14.5000),
        )

        assertTrue(AutomaticRouteDetector.detect(points).isEmpty())
    }

    private fun point(id: Long, timestamp: Long, lat: Double, lon: Double) = LocationPointEntity(
        id = id,
        tripId = 1,
        timestamp = timestamp + 1_000,
        lat = lat,
        lon = lon,
        accuracyMeters = 8f,
        segmentMeters = 100.0,
    )
}
