package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lukabencina.kilometrina.data.SavedPlace
import si.lukabencina.kilometrina.data.SavedPlaceCodec
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.ui.HomeUiState

class SavedPlaceCodecTest {
    @Test
    fun savedPlaceRoundTripsWithSlovenianCharacters() {
        val place = SavedPlace(
            id = "place-1",
            name = "Ordinacija Šiška",
            address = "Celovška cesta 100, Ljubljana",
            defaultPurpose = "Obisk stranke – predstavitev",
        )

        assertEquals(place, SavedPlaceCodec.decode(SavedPlaceCodec.encode(place)))
    }

    @Test
    fun recentLocationsAreUniqueAndNewestFirst() {
        val newer = TripEntity(
            id = 2,
            startTime = 2_000,
            endTime = 3_000,
            startLat = 0.0,
            startLon = 0.0,
            startAddress = "Prodent, Ljubljana",
            endAddress = "Ordinacija A, Kranj",
        )
        val older = TripEntity(
            id = 1,
            startTime = 1_000,
            endTime = 1_500,
            startLat = 0.0,
            startLon = 0.0,
            startAddress = "Prodent, Ljubljana",
            endAddress = "Ordinacija B, Celje",
        )

        val locations = HomeUiState(trips = listOf(newer, older)).recentLocations

        assertEquals("Prodent, Ljubljana", locations.first())
        assertEquals(3, locations.size)
        assertTrue(locations.contains("Ordinacija B, Celje"))
    }
}
