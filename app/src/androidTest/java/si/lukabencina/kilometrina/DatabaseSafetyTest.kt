package si.lukabencina.kilometrina

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.location.Location
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import si.lukabencina.kilometrina.data.*

@RunWith(AndroidJUnit4::class)
class DatabaseSafetyTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun trip()=TripEntity(id=1,startTime=1000,startLat=46.0,startLon=14.0,startAddress="Dom")

    @Test fun interruptedDistanceWriteRollsBackPointAndResumesWithoutLostMeters()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val dao=db.tripDao()
            dao.insertTrip(trip())
            dao.insertPoint(LocationPointEntity(id=1,tripId=1,timestamp=1000,lat=46.0,lon=14.0,accuracyMeters=5f,segmentMeters=0.0))
            val repo=TripRepository(context,dao,SavedPlaceRepository(context),SettingsRepository(context),CalendarSuggestionRepository(context))
            val first=Location("test").apply { latitude=46.001;longitude=14.0;time=6000;accuracy=5f }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_distance BEFORE UPDATE OF distanceMeters ON trips BEGIN SELECT RAISE(ABORT,'injected distance failure'); END")
            var failed=false
            try { repo.appendLocation(1,first) } catch(error:Exception) { failed=true }
            assertTrue(failed)
            assertEquals(1,dao.getAllPoints().size)
            assertEquals(0.0,dao.getAllTrips().single().distanceMeters,0.0)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_distance")
            repo.appendLocation(1,first)
            repo.appendLocation(1,Location(first).apply { latitude=46.002;time=11000 })
            assertEquals(3,dao.getAllPoints().size)
            assertEquals(dao.getAllPoints().sumOf { it.segmentMeters },dao.getAllTrips().single().distanceMeters,0.0001)
        } finally { db.close() }
    }

    @Test fun duplicateReceiptRestoreLeavesExistingDatabaseIntact()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val dao=db.tripDao()
            dao.insertTrip(trip().copy(endTime=2000,distanceMeters=12345.0))
            val original=AttachmentEntity(id=1,tripId=1,displayName="original.pdf",mimeType="application/pdf",data=byteArrayOf(1,2,3),createdAt=1)
            dao.insertAttachment(original)
            val repo=BackupRepository(db,SettingsRepository(context),SavedPlaceRepository(context),VehicleRepository(context))
            val before=BackupCodec.decode(repo.createBackupJson())
            val invalid=BackupCodec.encode(before.copy(attachments=listOf(original,original.copy(displayName="second.pdf",data=byteArrayOf(9)))))
            var rejected=false
            try { repo.restoreBackupJson(invalid) } catch(error:IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            assertEquals(before.trips,dao.getAllTrips())
            assertArrayEquals(original.data,dao.getAllAttachments().single().data)
        } finally { db.close() }
    }
}
