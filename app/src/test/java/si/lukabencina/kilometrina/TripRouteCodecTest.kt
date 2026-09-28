package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.data.TripRouteCodec
import si.lukabencina.kilometrina.data.routeAddresses
import si.lukabencina.kilometrina.data.routeStops

class TripRouteCodecTest {
    @Test
    fun roundTripKeepsOrderedStopsAndCleansWhitespace() {
        val encoded = TripRouteCodec.encode(listOf("  Celje  ", "", "Maribor"))

        assertEquals(listOf("Celje", "Maribor"), TripRouteCodec.decode(encoded))
    }

    @Test
    fun malformedJsonFallsBackToEmptyStops() {
        assertTrue(TripRouteCodec.decode("not-json").isEmpty())
    }

    @Test
    fun routeAddressesIncludeStartStopsAndEndInOrder() {
        val trip = TripEntity(
            id = 1,
            startTime = 100,
            endTime = 200,
            startLat = 46.0,
            startLon = 14.0,
            startAddress = "Ljubljana",
            endAddress = "Dravograd",
            routeStopsJson = TripRouteCodec.encode(listOf("Celje", "Maribor")),
        )

        assertEquals(listOf("Celje", "Maribor"), trip.routeStops())
        assertEquals(listOf("Ljubljana", "Celje", "Maribor", "Dravograd"), trip.routeAddresses())
    }
}
