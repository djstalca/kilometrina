package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lukabencina.kilometrina.data.GpsQuality
import si.lukabencina.kilometrina.data.GpsQualityEvaluator
import si.lukabencina.kilometrina.data.LocationPointEntity

class GpsQualityEvaluatorTest {
    @Test
    fun missingWhenTooFewPoints() {
        assertEquals(GpsQuality.MISSING, GpsQualityEvaluator.evaluate(emptyList()))
    }

    @Test
    fun goodForFrequentAccuratePoints() {
        val points = (0..4).map { index ->
            LocationPointEntity(
                id = (index + 1).toLong(),
                tripId = 1,
                timestamp = 1_000L + index * 5_000L,
                lat = 46.0 + index * 0.0001,
                lon = 14.0,
                accuracyMeters = 6f,
                segmentMeters = 10.0,
            )
        }
        assertEquals(GpsQuality.GOOD, GpsQualityEvaluator.evaluate(points))
    }

    @Test
    fun poorWhenRouteHasLongGap() {
        val points = listOf(
            LocationPointEntity(1, 1, 1_000, 46.0, 14.0, 8f, 0.0),
            LocationPointEntity(2, 1, 130_000, 46.1, 14.1, 8f, 1_000.0),
        )
        assertEquals(GpsQuality.POOR, GpsQualityEvaluator.evaluate(points))
    }
}
