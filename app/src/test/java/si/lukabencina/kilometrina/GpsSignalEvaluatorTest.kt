package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lukabencina.kilometrina.location.GpsSignalEvaluator
import si.lukabencina.kilometrina.location.GpsSignalQuality

class GpsSignalEvaluatorTest {
    @Test
    fun reportsGoodFreshAccurateFix() {
        assertEquals(
            GpsSignalQuality.Good,
            GpsSignalEvaluator.quality(true, true, 2.0, 7f),
        )
    }

    @Test
    fun reportsFairForModerateAccuracy() {
        assertEquals(
            GpsSignalQuality.Fair,
            GpsSignalEvaluator.quality(true, true, 4.0, 20f),
        )
    }

    @Test
    fun reportsPoorWhenFixIsDelayed() {
        assertEquals(
            GpsSignalQuality.Poor,
            GpsSignalEvaluator.quality(true, true, 20.0, 8f),
        )
    }

    @Test
    fun reportsLostWhenFixIsOlderThanThirtySeconds() {
        assertEquals(
            GpsSignalQuality.Lost,
            GpsSignalEvaluator.quality(true, true, 31.0, 8f),
        )
    }

    @Test
    fun reportsUnavailableWhenProviderSaysLocationUnavailable() {
        assertEquals(
            GpsSignalQuality.Unavailable,
            GpsSignalEvaluator.quality(true, false, 1.0, 5f),
        )
    }
}
