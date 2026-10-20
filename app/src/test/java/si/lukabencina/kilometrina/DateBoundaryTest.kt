package si.lukabencina.kilometrina
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.ZoneId
import si.lukabencina.kilometrina.data.*
import si.lukabencina.kilometrina.ui.*
class DateBoundaryTest {
 private val zone=ZoneId.of("Europe/Ljubljana")
 private fun trip(iso:String,id:Long)=TripEntity(id=id,startTime=Instant.parse(iso).toEpochMilli(),endTime=Instant.parse(iso).toEpochMilli()+3600000,startLat=46.0,startLon=14.0,startAddress="Dom",endAddress="Prodent",distanceMeters=1000.0)
 @Test fun monthAndYearUseLocalStartTime(){
  val old=trip("2025-12-31T22:59:59Z",1);val fresh=trip("2025-12-31T23:00:00Z",2)
  assertEquals(1,StatisticsCalculator.calculate(listOf(old,fresh),2025,zone).tripCount)
  assertEquals(1,StatisticsCalculator.calculate(listOf(old,fresh),2026,zone).months.first().tripCount)
  val march=trip("2026-03-31T21:59:59Z",3);val april=trip("2026-03-31T22:00:00Z",4)
  val stats=StatisticsCalculator.calculate(listOf(march,april),2026,zone)
  assertEquals(1,stats.months[2].tripCount);assertEquals(1,stats.months[3].tripCount)
 }
 @Test fun dstRepeatedHourDoesNotDuplicateOrExcludeTrips(){
  val stats=StatisticsCalculator.calculate(listOf(trip("2026-10-25T00:30:00Z",1),trip("2026-10-25T01:30:00Z",2)),2026,zone)
  assertEquals(2,stats.months[9].tripCount);assertEquals(2.0,stats.distanceKm,0.0)
 }
 @Test fun unfinishedAndPrivateNeverReimburse(){
  val t=trip("2026-10-01T08:00:00Z",1)
  val stats=StatisticsCalculator.calculate(listOf(t,t.copy(id=2,tripKind=TripKinds.PRIVATE,parkingCents=500,tollsCents=700),t.copy(id=3,endTime=null)),2026,zone)
  assertEquals(1,stats.tripCount);assertEquals(0.43,stats.totalAmount,0.00001)
 }
}
