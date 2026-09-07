package si.lukabencina.kilometrina

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lukabencina.kilometrina.data.TripRules

class TripRulesTest {
    @Test
    fun endMustBeAfterStart() {
        assertTrue(TripRules.hasValidTimeRange(1_000L, 2_000L))
        assertFalse(TripRules.hasValidTimeRange(2_000L, 2_000L))
        assertFalse(TripRules.hasValidTimeRange(3_000L, 2_000L))
    }
}
