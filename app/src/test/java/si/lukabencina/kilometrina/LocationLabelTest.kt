package si.lukabencina.kilometrina

import org.junit.Assert.*
import org.junit.Test
import si.lukabencina.kilometrina.data.preciseLocationLabel
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.ui.formatTripRoute

class LocationLabelTest {
    @Test fun streetAndHouseNumberTakePriorityOverCityForEveryNewLocation() {
        assertEquals("Zvezna ulica 2A, Ljubljana", preciseLocationLabel("Zvezna ulica","2A","Ljubljana",countryCode="SI",countryName="Slovenija"))
        assertEquals("Zvezna ulica 2A, Ljubljana", preciseLocationLabel("Zvezna ulica 2A","2a","Ljubljana"))
    }
    @Test fun missingStreetFieldsRetainFullProviderAddress() {
        assertEquals("Cesta 15, 1000 Ljubljana", preciseLocationLabel(null,null,"Ljubljana",addressLine="Cesta 15, 1000 Ljubljana"))
        assertEquals("Cesta 15, Bežigrad", preciseLocationLabel("Cesta","15",null,district="Bežigrad"))
    }
    @Test fun coarseCityIsUsedOnlyWhenNoPreciseAddressExists() {
        assertEquals("Ljubljana",preciseLocationLabel(null,null,"Ljubljana"))
        assertEquals("Ljubljana",preciseLocationLabel(null,null,"Ljubljana",addressLine="Slovenija",countryName="Slovenija"))
        assertNull(preciseLocationLabel(null,null,null,countryCode="SI",countryName="Slovenija"))
    }
    @Test fun foreignCountryIsKeptWithoutAddingItToDomesticAddresses() {
        assertEquals("Ulica 10, Rijeka, Hrvatska", preciseLocationLabel("Ulica","10","Rijeka",countryCode="HR",countryName="Hrvatska"))
    }
    @Test fun existingManualLabelsAreNotShortenedOrReplacedInRouteFormatting() {
        val trip=TripEntity(startTime=1,startLat=46.0,startLon=14.0,startAddress="Moja ordinacija, Zvezna ulica 2A, Ljubljana",
            endAddress="Ročno potrjen cilj, Ulica 15, Kranj")
        assertEquals("${trip.startAddress} → ${trip.endAddress}",formatTripRoute(trip))
    }
}
