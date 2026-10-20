package si.lukabencina.kilometrina

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import si.lukabencina.kilometrina.data.*
import si.lukabencina.kilometrina.ui.*

class ReleaseSafetyTest {
    private fun trip() = TripEntity(id=1, startTime=1000, endTime=2000, startLat=46.0, startLon=14.0,
        startAddress="Ž".repeat(501), endAddress="Prodent", distanceMeters=100.0,
        routeStopsJson=TripRouteCodec.encode(listOf("Črnomelj", "Žužemberk")),
        vehicleName="Old Škoda", registrationPlate="LJ-OLD", tripKind=TripKinds.BUSINESS)
    private fun backup() = BackupData(generatedAt=1, settings=AppSettings(), savedPlaces=emptyList(),
        trips=listOf(trip()), points=emptyList())

    @Test fun restoresOwnLongAddressesAndPreservesLegacyRates() {
        val data = backup().copy(trips=listOf(trip().copy(ratePerKm=11.0)))
        assertEquals(data.trips, BackupCodec.decode(BackupCodec.encode(data)).trips)
    }
    @Test fun currentBackupDoesNotAssignTheDefaultCarToHistoricalTripsWithoutACar() {
        val vehicle=Vehicle(id="new",name="Current car",registrationPlate="LJ-NEW")
        val data=backup().copy(vehicles=VehicleState(listOf(vehicle),vehicle.id),
            trips=listOf(trip().copy(vehicleId="",vehicleName="",registrationPlate="")))
        assertEquals(data.trips,BackupCodec.decode(BackupCodec.encode(data)).trips)
    }
    @Test fun rejectsDuplicateOrNonpositiveAttachmentIdsBeforeRestore() {
        val receipt = AttachmentEntity(id=9, tripId=1, displayName="one.pdf", mimeType="application/pdf", data=byteArrayOf(1))
        for (receipts in listOf(listOf(receipt, receipt.copy(data=byteArrayOf(2))), listOf(receipt.copy(id=0)), listOf(receipt.copy(id=-1)))) {
            assertThrows(IllegalArgumentException::class.java) {
                BackupCodec.decode(BackupCodec.encode(backup().copy(attachments=receipts)))
            }
        }
    }
    @Test fun rejectsPresentButMalformedOptionalArrays() {
        for (value in listOf("not an array", 5, JSONObject.NULL)) {
            val raw=JSONObject(BackupCodec.encode(backup()))
            raw.getJSONArray("trips").getJSONObject(0).put("routeStops",value)
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(raw.toString()) }
            val receipts=JSONObject(BackupCodec.encode(backup())).put("attachments",value)
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(receipts.toString()) }
        }
    }
    @Test fun oldSchemasWithMissingOptionalFieldsRemainReadable() {
        for (schema in 1..6) {
            val raw=JSONObject(BackupCodec.encode(backup())).put("schemaVersion",schema)
            val t=raw.getJSONArray("trips").getJSONObject(0)
            if(schema<2) raw.remove("vehicles")
            if(schema<3) { raw.remove("attachments"); listOf("tripKind","gpsQuality","gpsWarning","calendarEventId","calendarTitle").forEach { t.remove(it) } }
            if(schema<4) listOf("description","routeStops").forEach { t.remove(it) }
            if(schema<5) t.remove("routeDetectionVersion")
            if(schema<6) t.remove("routeReviewPending")
            val restored=BackupCodec.decode(raw.toString()).trips.single()
            assertEquals(trip().distanceMeters,restored.distanceMeters,0.0)
            assertEquals(trip().startAddress,restored.startAddress)
            assertEquals("Old Škoda",restored.vehicleName)
        }
    }
    @Test fun everyInvalidNumberIsRejectedIndependentlyInEveryField() {
        for (bad in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1.0)) {
            assertFalse(TripRules.areValidNumbers(bad,0.43,0.0,0.0))
            assertFalse(TripRules.areValidNumbers(10.0,bad,0.0,0.0))
            assertFalse(TripRules.areValidNumbers(10.0,0.43,bad,0.0))
            assertFalse(TripRules.areValidNumbers(10.0,0.43,0.0,bad))
        }
        assertFalse(TripRules.areValidNumbers(10.0,11.0,0.0,0.0))
        assertFalse(TripRules.areValidNumbers(Double.MAX_VALUE,0.43,0.0,0.0))
        assertFalse(TripRules.areValidNumbers(10.0,0.43,Int.MAX_VALUE/100.0+1,0.0))
        assertTrue(TripRules.areValidNumbers(0.0,10.0,Int.MAX_VALUE/100.0,Int.MAX_VALUE/100.0))
    }
    @Test fun printedRowsSummaryAndStatisticsUseTheSameCentPolicy() {
        val trips=listOf(trip().copy(startAddress="Dom"),trip().copy(id=2,startAddress="Dom"))
        assertEquals(0.04,tripCompensation(trips.first()),0.0)
        assertEquals(0.08,ReportCalculator.summarize(trips).totalAmount,0.0)
        assertTrue(CsvExporter.build(trips).contains("\"SKUPAJ EUR\";\"0,08\""))
        assertEquals(0.05,tripCompensation(trip().copy(distanceMeters=100.0,ratePerKm=0.45)),0.0)
        val costs=trip().copy(parkingCents=Int.MAX_VALUE,tollsCents=Int.MAX_VALUE)
        assertEquals(42949672.94,tripAdditionalCosts(costs),0.0)
        assertEquals(0.0,tripTotalCost(costs.copy(tripKind=TripKinds.PRIVATE)),0.0)
    }
}
