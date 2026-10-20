package si.lukabencina.kilometrina

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.*
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import si.lukabencina.kilometrina.data.*
import si.lukabencina.kilometrina.ui.*

@RunWith(AndroidJUnit4::class)
class ExportMatrixTest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val dir get() = File(context.getExternalFilesDir(null), "release-regression").apply { mkdirs() }
  private val month = YearMonth.of(2026,10)
  private val settings = AppSettings(driverName="Črt Šušterič", companyName="Žična oprema", vehicleName="Legacy", registrationPlate="LJ-OLD")
  private fun epoch(s:String) = LocalDateTime.parse(s).atZone(ZoneId.of("Europe/Ljubljana")).toInstant().toEpochMilli()
  private fun trip(i:Int) = TripEntity(id=i.toLong(), startTime=epoch("2026-10-01T08:00:00")+i*86400000L,
    endTime=epoch("2026-10-01T09:00:00")+i*86400000L, startLat=46.0,startLon=14.0,
    startAddress="Škofja Loka",endAddress="Prodent, Zvezna ulica 2A", distanceMeters=10000.0+i*1000,
    purpose="Servis čeljusti",description="Vožnja $i – Žična oprema",routeStopsJson=TripRouteCodec.encode(listOf("Črnomelj","Žužemberk")),
    routeDetectionVersion=1, vehicleId="v${i%3}",vehicleName="Vozilo ${i%3}",registrationPlate="LJ-${i%3}",parkingCents=250,tollsCents=700)
  @Test fun generateNativeExportMatrix() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Ljubljana"))
    val cases=linkedMapOf<String,List<TripEntity>>()
    for(n in listOf(1,4,8,10,15,30)) cases["trips_$n"]=(1..n).map(::trip).reversed()
    cases["multiple_vehicles"]=(1..6).map(::trip)
    cases["long_routes"]=listOf(trip(1).copy(startAddress="Škofja Loka ".repeat(40),endAddress="Žužemberk ".repeat(45),routeStopsJson=TripRouteCodec.encode(List(20){"Črnomelj postanek $it "+"dolgo ".repeat(30)})))
    cases["long_description"]=listOf(trip(1).copy(purpose="Namen ".repeat(30),description="Č Š Ž opis\n".repeat(45)))
    cases["parking_only"]=listOf(trip(1).copy(distanceMeters=0.0,tollsCents=0))
    cases["toll_only"]=listOf(trip(1).copy(distanceMeters=0.0,parkingCents=0))
    cases["parking_toll"]=listOf(trip(1).copy(distanceMeters=0.0))
    cases["zero_expenses"]=listOf(trip(1).copy(parkingCents=0,tollsCents=0))
    cases["mixed_private"]=(1..8).map{trip(it).copy(tripKind=if(it%2==0) TripKinds.PRIVATE else TripKinds.BUSINESS)}
    cases["many_vehicles"]=(1..30).map{trip(it).copy(vehicleId="v$it",vehicleName="Vozilo $it",registrationPlate="LJ-$it")}
    cases["empty_fields"]=listOf(trip(1).copy(startAddress="",endAddress=null,purpose="",description="",vehicleName="",registrationPlate="",routeStopsJson="[]"))
    cases["rounding"]=(1..2).map{trip(it).copy(distanceMeters=100.0,parkingCents=0,tollsCents=0)}
    cases["overflow_costs"]=listOf(trip(1).copy(distanceMeters=0.0,parkingCents=Int.MAX_VALUE,tollsCents=Int.MAX_VALUE))
    val manifest=JSONArray()
    for((name,trips) in cases) {
      File(dir,"$name.pdf").outputStream().use{PdfReportExporter.write(it,trips,month,settings)}
      File(dir,"$name.csv").writeText("\uFEFF"+CsvExporter.build(trips,month,settings),Charsets.UTF_8)
      val filtered=trips.filter{it.endTime!=null && it.tripKind!=TripKinds.PRIVATE}.sortedBy{it.startTime}
      val spec=JSONObject().put("name",name).put("count",filtered.size)
      val rows=JSONArray()
      filtered.forEach{t->rows.put(JSONObject().put("id",t.id).put("date",formatDate(t.startTime)).put("description",t.description).put("purpose",t.purpose)
        .put("km",t.distanceMeters/1000.0).put("rate",t.ratePerKm).put("parking",t.parkingCents/100.0).put("tolls",t.tollsCents/100.0)
        .put("vehicle",t.vehicleName).put("plate",t.registrationPlate).put("route",formatTripRoute(t,false)))}
      spec.put("rows",rows); manifest.put(spec)
    }
    File(dir,"export-manifest.json").writeText(manifest.toString(2))
    val packageResults=JSONArray()
    val trips=(1..4).map(::trip)+trip(5).copy(tripKind=TripKinds.PRIVATE)+trip(6).copy(startTime=epoch("2026-11-01T00:00:00"))
    fun attachment(id:Long,tripId:Long,name:String)=AttachmentEntity(id=id,tripId=tripId,displayName=name,mimeType="application/pdf",data="ČŠŽ receipt $id".toByteArray(),createdAt=id)
    val attachmentCases=linkedMapOf(
      "empty" to emptyList(),
      "multiple" to listOf(attachment(1,1,"račun.pdf"),attachment(2,1,"parkiranje.pdf"),attachment(3,2,"račun.pdf"),attachment(4,5,"private.pdf"),attachment(5,6,"november.pdf")),
      "duplicate_name" to listOf(attachment(1,1,"račun.pdf"),attachment(2,1,"račun.pdf")),
      "sanitized_collision" to listOf(attachment(1,1,"a:b.pdf"),attachment(2,1,"a?b.pdf")),
      "long_collision" to listOf(attachment(1,1,"a".repeat(120)+"1.pdf"),attachment(2,1,"a".repeat(120)+"2.pdf")),
      "invalid_filename" to listOf(attachment(1,1,"..\\račun:/?*.pdf")))
    for((name,attachments) in attachmentCases){
      val result=JSONObject().put("name",name)
      try {File(dir,"package_$name.zip").outputStream().use{MonthPackageExporter.write(it,trips,month,settings,attachments)};result.put("generated",true)}
      catch(e:Exception){result.put("generated",false).put("error",e.toString())}
      packageResults.put(result)
    }
    File(dir,"package-results.json").writeText(packageResults.toString(2))
    assertTrue(File(dir,"trips_30.pdf").length()>0)
    assertTrue((0 until packageResults.length()).all { packageResults.getJSONObject(it).getBoolean("generated") })
  }
}
