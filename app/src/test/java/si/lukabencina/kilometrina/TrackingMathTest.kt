package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lukabencina.kilometrina.location.SegmentRejectionReason
import si.lukabencina.kilometrina.location.TrackingMath

class TrackingMathTest {
    @Test
    fun rejectsGpsJitter() {
        val result = TrackingMath.evaluateSegment(2.5, 5.0, 5f, 5f)
        assertFalse(result.accepted)
        assertEquals(SegmentRejectionReason.GpsJitter, result.rejectionReason)
    }

    @Test
    fun adaptiveJitterGateRejectsDriftWithinPoorerAccuracyRadius() {
        val result = TrackingMath.evaluateSegment(10.0, 5.0, 20f, 18f)
        assertFalse(result.accepted)
        assertEquals(SegmentRejectionReason.GpsJitter, result.rejectionReason)
    }

    @Test
    fun rejectsLowAccuracyFix() {
        val result = TrackingMath.evaluateSegment(80.0, 5.0, 65f, 8f)
        assertFalse(result.accepted)
        assertEquals(SegmentRejectionReason.LowAccuracy, result.rejectionReason)
    }

    @Test
    fun rejectsStaleFix() {
        val result = TrackingMath.evaluateSegment(70.0, 5.0, 8f, 8f, fixAgeSeconds = 31.0)
        assertFalse(result.accepted)
        assertEquals(SegmentRejectionReason.StaleFix, result.rejectionReason)
    }

    @Test
    fun rejectsImpossibleJump() {
        val result = TrackingMath.evaluateSegment(800.0, 5.0, 5f, 5f)
        assertFalse(result.accepted)
        assertEquals(SegmentRejectionReason.ImpossibleSpeed, result.rejectionReason)
    }

    @Test
    fun acceptsNormalDrivingSegment() {
        assertTrue(TrackingMath.shouldAcceptSegment(70.0, 5.0, 6f))
    }
}
