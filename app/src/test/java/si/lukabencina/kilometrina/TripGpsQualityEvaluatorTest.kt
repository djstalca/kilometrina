package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lukabencina.kilometrina.data.LocationPointEntity
import si.lukabencina.kilometrina.data.TripGpsQuality
import si.lukabencina.kilometrina.data.TripGpsQualityEvaluator

class TripGpsQualityEvaluatorTest {
    private fun point(id: Long, timestamp: Long, accuracy: Float) = LocationPointEntity(
        id = id,
        tripId = 1,
        timestamp = timestamp,
        lat = 46.0,
        lon = 14.0,
        accuracyMeters = accuracy,
        segmentMeters = if (id == 1L) 0.0 else 100.0,
    )

    @Test
    fun noGpsWithSinglePoint() {
        assertEquals(TripGpsQuality.NoGps, TripGpsQualityEvaluator.evaluate(listOf(point(1, 0, 5f))).quality)
    }

    @Test
    fun goodForRegularAccurateFixes() {
        val points = (0L..10L).map { point(it + 1, it * 5_000, 8f) }
        assertEquals(TripGpsQuality.Good, TripGpsQualityEvaluator.evaluate(points).quality)
    }

    @Test
    fun reviewForLongGap() {
        val points = listOf(point(1, 0, 8f), point(2, 120_000, 8f))
        assertEquals(TripGpsQuality.Review, TripGpsQualityEvaluator.evaluate(points).quality)
    }
}
