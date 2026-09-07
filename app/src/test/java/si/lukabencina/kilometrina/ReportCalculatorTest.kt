package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.ui.ReportCalculator

class ReportCalculatorTest {
    @Test
    fun summarizesMileageAndAdditionalCosts() {
        val trips = listOf(
            trip(1, 100_000.0, 0.43, 250, 700),
            trip(2, 50_000.0, 0.43, 0, 300),
        )

        val summary = ReportCalculator.summarize(trips)

        assertEquals(2, summary.tripCount)
        assertEquals(150.0, summary.distanceKm, 0.001)
        assertEquals(64.5, summary.mileageAmount, 0.001)
        assertEquals(2.5, summary.parkingAmount, 0.001)
        assertEquals(10.0, summary.tollsAmount, 0.001)
        assertEquals(77.0, summary.totalAmount, 0.001)
    }

    @Test
    fun ignoresUnfinishedTrips() {
        val unfinished = trip(1, 30_000.0, 0.43, 0, 0).copy(endTime = null)
        val summary = ReportCalculator.summarize(listOf(unfinished))
        assertEquals(0, summary.tripCount)
        assertEquals(0.0, summary.totalAmount, 0.001)
    }

    private fun trip(
        id: Long,
        distanceMeters: Double,
        rate: Double,
        parkingCents: Int,
        tollsCents: Int,
    ) = TripEntity(
        id = id,
        startTime = 1_700_000_000_000 + id * 10_000,
        endTime = 1_700_000_600_000 + id * 10_000,
        startLat = 46.0,
        startLon = 14.0,
        startAddress = "Ljubljana",
        endAddress = "Zagreb",
        distanceMeters = distanceMeters,
        purpose = "Obisk stranke",
        ratePerKm = rate,
        parkingCents = parkingCents,
        tollsCents = tollsCents,
    )
}
