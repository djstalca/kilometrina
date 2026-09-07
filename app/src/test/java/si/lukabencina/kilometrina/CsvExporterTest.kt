package si.lukabencina.kilometrina

import org.junit.Assert.assertTrue
import org.junit.Test
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.ui.CsvExporter

class CsvExporterTest {
    @Test
    fun csvContainsTripAndAllCosts() {
        val trip = TripEntity(
            id = 1,
            startTime = 1_700_000_000_000,
            endTime = 1_700_000_600_000,
            startLat = 46.0,
            startLon = 14.0,
            startAddress = "Ljubljana, Slovenija",
            endLat = 45.8,
            endLon = 15.9,
            endAddress = "Zagreb, Hrvaška",
            distanceMeters = 100_000.0,
            purpose = "Obisk stranke",
            ratePerKm = 0.43,
            parkingCents = 250,
            tollsCents = 700,
        )

        val csv = CsvExporter.build(listOf(trip))

        assertTrue(csv.contains("Obisk stranke"))
        assertTrue(csv.contains("100,00"))
        assertTrue(csv.contains("43,00"))
        assertTrue(csv.contains("2,50"))
        assertTrue(csv.contains("7,00"))
        assertTrue(csv.contains("52,50"))
        assertTrue(csv.contains("Parkirnina EUR"))
        assertTrue(csv.contains("Cestnina EUR"))
        assertTrue(csv.contains("Skupaj EUR"))
    }
}
