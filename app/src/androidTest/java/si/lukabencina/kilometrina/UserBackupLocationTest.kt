package si.lukabencina.kilometrina

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import si.lukabencina.kilometrina.data.*
import java.io.File

/** An optional private fixture supplied over adb, never embedded in the APK or repository. */
class UserBackupLocationTest {
    @Test fun suppliedBackupPreservesHistoricalTripAndPlaceData()=runBlocking<Unit> {
        val fixture=InstrumentationRegistry.getArguments().getString("backupFixture")
        assumeNotNull(fixture)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val raw=File(requireNotNull(fixture)).readText()
        val incoming=BackupCodec.decode(raw)
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val backup=BackupRepository(db,SettingsRepository(context),SavedPlaceRepository(context),VehicleRepository(context))
            backup.restoreBackupJson(raw)
            val after=backup.createBackupJson()
            val decoded=BackupCodec.decode(after)
            assertEquals(incoming.trips.associateBy { it.id },decoded.trips.associateBy { it.id })
            assertEquals(incoming.points.associateBy { it.id },decoded.points.associateBy { it.id })
            assertEquals(incoming.savedPlaces,decoded.savedPlaces)
            assertEquals(incoming.vehicles,decoded.vehicles)
            assertEquals(incoming.settings,decoded.settings)
            assertEquals(incoming.expenses.associateBy { it.id },decoded.expenses.associateBy { it.id })
            File(context.getExternalFilesDir(null),"backup-location-roundtrip.json").writeText(after)
        } finally { db.close() }
    }
}
