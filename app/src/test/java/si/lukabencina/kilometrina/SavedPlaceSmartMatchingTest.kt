package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lukabencina.kilometrina.data.SavedPlace
import si.lukabencina.kilometrina.data.SavedPlaceCodec
import si.lukabencina.kilometrina.data.SavedPlaceRepository

class SavedPlaceSmartMatchingTest {
    @Test
    fun codecKeepsCoordinatesAndRadius() {
        val place = SavedPlace(
            id = "prodent",
            name = "Prodent Šiška",
            address = "Ljubljana",
            defaultPurpose = "Obisk stranke",
            lat = 46.0712,
            lon = 14.4891,
            matchRadiusMeters = 350,
        )

        assertEquals(place, SavedPlaceCodec.decode(SavedPlaceCodec.encode(place)))
    }

    @Test
    fun oldFourFieldCodecRemainsReadable() {
        val legacy = SavedPlace(id = "legacy", name = "Dom", address = "Ljubljana")
        val encodedLegacy = SavedPlaceCodec.encode(legacy).split('|').take(4).joinToString("|")
        val decoded = SavedPlaceCodec.decode(encodedLegacy)

        assertEquals("Dom", decoded?.name)
        assertNull(decoded?.lat)
        assertEquals(250, decoded?.matchRadiusMeters)
    }

    @Test
    fun choosesNearestPlaceInsideConfiguredRadius() {
        val places = listOf(
            SavedPlace(id = "a", name = "A", address = "A", lat = 46.0500, lon = 14.5000, matchRadiusMeters = 500),
            SavedPlace(id = "b", name = "B", address = "B", lat = 46.0510, lon = 14.5010, matchRadiusMeters = 500),
        )

        val match = SavedPlaceRepository.nearestPlace(places, 46.0509, 14.5009)

        assertEquals("B", match?.place?.name)
        assertTrue(requireNotNull(match).distanceMeters < 30.0)
    }

    @Test
    fun doesNotMatchOutsideRadius() {
        val place = SavedPlace(id = "a", name = "A", address = "A", lat = 46.0500, lon = 14.5000, matchRadiusMeters = 100)
        assertNull(SavedPlaceRepository.nearestPlace(listOf(place), 46.0600, 14.5100))
    }
}
