package si.lukabencina.kilometrina

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lukabencina.kilometrina.location.TrackingMath

class TrackingMathTest {
    @Test
    fun rejectsGpsJitter() {
        assertFalse(TrackingMath.shouldAcceptSegment(2.5, 5.0, 5f))
    }

    @Test
    fun rejectsLowAccuracyFix() {
        assertFalse(TrackingMath.shouldAcceptSegment(20.0, 5.0, 65f))
    }

    @Test
    fun rejectsImpossibleJump() {
        assertFalse(TrackingMath.shouldAcceptSegment(800.0, 5.0, 5f))
    }

    @Test
    fun acceptsNormalDrivingSegment() {
        assertTrue(TrackingMath.shouldAcceptSegment(70.0, 5.0, 6f))
    }
}
