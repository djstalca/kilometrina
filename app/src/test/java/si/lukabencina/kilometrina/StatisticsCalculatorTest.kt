package si.lukabencina.kilometrina

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.ui.StatisticsCalculator

class StatisticsCalculatorTest {
    @Test
    fun calculatesMonthsRoutesVehiclesAndTotals() {
        val trips = listOf(
            trip("2026-01-05T08:00:00", "Dom", "Prodent", 20_000.0, "VW Passat", "LJ-AAA", 100),
            trip("2026-01-06T08:00:00", "Dom", "Prodent", 22_000.0, "VW Passat", "LJ-AAA", 0),
            trip("2026-02-03T09:00:00", "Prodent", "Zagreb", 140_000.0, "Škoda Octavia", "LJ-BBB", 500),
            trip("2025-12-31T10:00:00", "Dom", "Staro", 5_000.0, "VW Passat", "LJ-AAA", 0),
        )

        val result = StatisticsCalculator.calculate(trips, 2026, ZoneOffset.UTC)

        assertEquals(3, result.tripCount)
        assertEquals(182.0, result.distanceKm, 0.001)
        assertEquals(2, result.months.first { it.month == 1 }.tripCount)
        assertEquals(1, result.months.first { it.month == 2 }.tripCount)
        assertEquals("Dom → Prodent", result.topRoutes.first().route)
        assertEquals(2, result.topRoutes.first().tripCount)
        assertEquals(2, result.vehicles.size)
        assertEquals(78.36, result.mileageAmount, 0.001)
        assertEquals(6.0, result.additionalCosts, 0.001)
        assertEquals(84.36, result.totalAmount, 0.001)
    }

    private fun trip(
        start: String,
        from: String,
        to: String,
        meters: Double,
        vehicle: String,
        plate: String,
        parkingCents: Int,
    ): TripEntity {
        val startTime = LocalDateTime.parse(start).toInstant(ZoneOffset.UTC).toEpochMilli()
        return TripEntity(
            id = startTime,
            startTime = startTime,
            endTime = startTime + 3_600_000,
            startLat = 46.0,
            startLon = 14.0,
            startAddress = from,
            endLat = 46.1,
            endLon = 14.1,
            endAddress = to,
            distanceMeters = meters,
            purpose = "Službena pot",
            ratePerKm = 0.43,
            parkingCents = parkingCents,
            tollsCents = 0,
            vehicleName = vehicle,
            registrationPlate = plate,
        )
    }
}
