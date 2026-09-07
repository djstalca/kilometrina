package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.BackupCodec
import si.lukabencina.kilometrina.data.BackupData
import si.lukabencina.kilometrina.data.LocationPointEntity
import si.lukabencina.kilometrina.data.SavedPlace
import si.lukabencina.kilometrina.data.TripEntity

class BackupCodecTest {
    @Test
    fun roundTripKeepsTripsPointsSettingsAndPlaces() {
        val data = BackupData(
            generatedAt = 1_800_000_000_000,
            settings = AppSettings(
                ratePerKm = 0.43,
                defaultPurpose = "Obisk stranke",
                driverName = "Luka Benčina",
                companyName = "Prodent International d.o.o.",
                vehicleName = "VW Passat",
                registrationPlate = "LJ-TEST",
            ),
            savedPlaces = listOf(SavedPlace(id = "p1", name = "Dom", address = "Srednje Gameljne", defaultPurpose = "Službena pot")),
            trips = listOf(
                TripEntity(
                    id = 7,
                    startTime = 1_800_000_000_000,
                    endTime = 1_800_000_600_000,
                    startLat = 46.1,
                    startLon = 14.5,
                    startAddress = "Ljubljana",
                    endLat = 45.8,
                    endLon = 15.9,
                    endAddress = "Zagreb",
                    distanceMeters = 140_500.0,
                    purpose = "Obisk stranke",
                    ratePerKm = 0.43,
                    tollsCents = 720,
                    parkingCents = 250,
                ),
            ),
            points = listOf(
                LocationPointEntity(
                    id = 11,
                    tripId = 7,
                    timestamp = 1_800_000_100_000,
                    lat = 46.05,
                    lon = 14.7,
                    accuracyMeters = 6f,
                    segmentMeters = 250.0,
                ),
            ),
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(data))

        assertEquals(data, decoded)
    }

    @Test
    fun rejectsBackupWithActiveTrip() {
        val data = BackupData(
            generatedAt = 1,
            settings = AppSettings(),
            savedPlaces = emptyList(),
            trips = listOf(
                TripEntity(
                    id = 1,
                    startTime = 100,
                    endTime = null,
                    startLat = 46.0,
                    startLon = 14.0,
                    startAddress = "Ljubljana",
                ),
            ),
            points = emptyList(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(BackupCodec.encode(data))
        }
    }
}
