package si.lukabencina.kilometrina

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import si.lukabencina.kilometrina.data.*
import si.lukabencina.kilometrina.ui.MonthPackageExporter
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.time.*
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ExpenseSafetyTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun trip(id:Long=1)=TripEntity(id=id,startTime=LocalDate.of(2026,10,2).atTime(8,0).atZone(ZoneId.of("Europe/Ljubljana")).toInstant().toEpochMilli(),
        startLat=46.0,startLon=14.0,startAddress="Dom")
    private fun expense(id:String="one",amount:Int=110,tripId:Long=1)=ExpenseEntity(id,tripId,ExpenseKinds.TOLL,amount)

    @Test fun concurrentCostsGpsAndStaleCompletionPreserveEveryWrite()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val dao=db.tripDao();val stale=trip();dao.insertTrip(stale)
            coroutineScope {
                repeat(40) { i->launch(Dispatchers.IO) { dao.saveExpense(expense("cost-$i")) } }
                repeat(20) { i->launch(Dispatchers.IO) { dao.appendPointAndDistance(LocationPointEntity(tripId=1,timestamp=i.toLong(),lat=46.0,lon=14.0,accuracyMeters=5f,segmentMeters=2.0)) } }
            }
            dao.completeTrip(stale.copy(endTime=stale.startTime+60000))
            assertEquals(4400,dao.getTrip(1)!!.tollsCents)
            assertEquals(40.0,dao.getTrip(1)!!.distanceMeters,0.0)
            assertEquals(20,dao.getAllPoints().size)
            // A picker/form opened before completion may still save to its original trip.
            repeat(2) { dao.saveExpense(expense("late")) }
            assertEquals(41,dao.getTripExpenses(1).size);assertEquals(4510,dao.getTrip(1)!!.tollsCents)
            val editorSnapshot=dao.getTrip(1)!!
            dao.saveExpense(expense("another",220))
            dao.updateCompletedDetails(editorSnapshot.copy(description="Urejeno"))
            assertEquals(4730,dao.getTrip(1)!!.tollsCents)
        } finally { db.close() }
    }

    @Test fun overflowingTotalRollsBackEntireNewEntry()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val dao=db.tripDao();dao.insertTrip(trip());dao.saveExpense(expense(amount=Int.MAX_VALUE))
            try { dao.saveExpense(expense("overflow",1));fail() } catch(expected:IllegalArgumentException) { }
            assertEquals(1,dao.getTripExpenses(1).size);assertEquals(Int.MAX_VALUE,dao.getTrip(1)!!.tollsCents)
        } finally { db.close() }
    }

    @Test fun receiptsAreAtomicBoundToCorrectTripAndRetainedOnDelete()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val dao=db.tripDao();dao.insertTrip(trip());dao.insertTrip(trip(2));dao.saveExpense(expense())
            val file=AttachmentEntity(tripId=2,displayName="racun.pdf",mimeType="application/pdf",data=byteArrayOf(1))
            try { dao.attachExpenseReceipt("one",file);fail() } catch(expected:IllegalArgumentException) { }
            assertTrue(dao.getAttachmentSummaries().isEmpty())
            dao.attachExpenseReceipt("one",file.copy(tripId=1))
            val attached=dao.getExpense("one")!!.attachmentId!!
            dao.saveExpense(expense(amount=220))
            assertEquals(attached,dao.getExpense("one")!!.attachmentId)
            dao.deleteExpense("one")
            assertEquals(0,dao.getTrip(1)!!.tollsCents);assertEquals(1,dao.getAttachmentSummaries().size)
            try { dao.attachExpenseReceipt("one",file.copy(tripId=1));fail() } catch(expected:IllegalArgumentException) { }
            assertEquals(1,dao.getAttachmentSummaries().size)
        } finally { db.close() }
    }

    @Test fun restartBackupRestoreAndZipKeepLargeReceiptAndLedger()=runBlocking<Unit> {
        val name="expense-restart-${UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        try {
            var dao=db.tripDao();val t=trip();dao.insertTrip(t);dao.saveExpense(expense())
            val bytes=ByteArray(8*1024*1024) { (it*31).toByte() }
            dao.attachExpenseReceipt("one",AttachmentEntity(tripId=1,displayName="račun.pdf",mimeType="application/pdf",data=bytes))
            db.close();db=Room.databaseBuilder(context,AppDatabase::class.java,name).build();dao=db.tripDao()
            assertEquals(110,dao.getTrip(1)!!.tollsCents)
            dao.completeTrip(t.copy(endTime=t.startTime+60000))
            val backup=BackupRepository(db,SettingsRepository(context),SavedPlaceRepository(context),VehicleRepository(context))
            val raw=backup.createBackupJson();val original=BackupCodec.decode(raw)
            dao.deleteAllTrips();backup.restoreBackupJson(raw)
            assertEquals(original.expenses,dao.getExpenses());assertArrayEquals(bytes,dao.getAllAttachments().single().data)
            val output=ByteArrayOutputStream()
            MonthPackageExporter.writeFromRepository(output,dao.getAllTrips(),YearMonth.of(2026,10),AppSettings(),dao.getAttachmentSummaries(),
                { attachment,stream->dao.copyAttachment(attachment,stream) },dao.getExpenses())
            val entries=mutableMapOf<String,ByteArray>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip->while(true) { val entry=zip.nextEntry ?: break;entries[entry.name]=zip.readBytes() } }
            val receipt=entries.keys.single { it.startsWith("priloge/") }
            assertArrayEquals(bytes,entries[receipt]);val csv=entries.getValue("stroski-2026-10.csv").toString(Charsets.UTF_8)
            assertTrue(csv.contains("1,10"));assertTrue(csv.contains(receipt));assertTrue(csv.contains("Cestnina"))
            dao.deleteAttachment(dao.getExpense("one")!!.attachmentId!!)
            assertNull(dao.getExpense("one")!!.attachmentId);assertEquals(110,dao.getTrip(1)!!.tollsCents)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
