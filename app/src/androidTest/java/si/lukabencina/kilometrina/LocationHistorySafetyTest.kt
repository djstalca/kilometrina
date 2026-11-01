package si.lukabencina.kilometrina

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import si.lukabencina.kilometrina.data.*

class LocationHistorySafetyTest {
    @Test fun finishingTripPreservesOriginalDepartureAndEveryHistoricalLabel()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val dao=db.tripDao()
            val old=TripEntity(id=1,startTime=1000,endTime=2000,startLat=46.0,startLon=14.0,
                startAddress="Ročno: moja pisarna",endAddress="Ročno: točen cilj",
                routeStopsJson=TripRouteCodec.encode(listOf("Ročno: stranka A")),routeDetectionVersion=0)
            val active=old.copy(id=2,endTime=null,startAddress="Že potrjen odhod",purpose="Preizkus",routeStopsJson="[]")
            dao.insertTrip(old);dao.insertTrip(active)
            val repo=TripRepository(context,dao,SavedPlaceRepository(context),SettingsRepository(context),CalendarSuggestionRepository(context))
            repo.finishTrip(null)
            assertEquals(old,dao.getTrip(1))
            assertEquals("Že potrjen odhod",dao.getTrip(2)!!.startAddress)
            assertNotNull(dao.getTrip(2)!!.endTime)
        } finally { db.close() }
    }
}
