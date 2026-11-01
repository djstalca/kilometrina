package si.lukabencina.kilometrina

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.YearMonth
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import si.lukabencina.kilometrina.data.*
import si.lukabencina.kilometrina.ui.MonthPackageExporter

@RunWith(AndroidJUnit4::class)
class AttachmentRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun legacyLargeBlobSurvivesReopenAndBackupWithoutMigration() = runBlocking<Unit> {
        val name = "receipt-recovery-${System.nanoTime()}.db"
        val original = ByteArray(8 * 1024 * 1024) { (it * 31).toByte() }
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val trip = TripEntity(id = 1, startTime = 1790841600000, endTime = 1790845200000,
            startLat = 46.0, startLon = 14.0, startAddress = "Dom", endAddress = "Prodent", distanceMeters = 12345.0)
        try {
            db.tripDao().insertTrip(trip)
            // These are the same schema-v6 rows written by 1.1.5. No cleanup or migration.
            db.tripDao().insertAttachment(AttachmentEntity(id = 1, tripId = 1, displayName = "račun.jpg",
                mimeType = "image/jpeg", data = original, createdAt = 1))
            db.tripDao().insertAttachment(AttachmentEntity(id = 2, tripId = 1, displayName = "račun.jpg",
                mimeType = "image/jpeg", data = original.copyOfRange(0, 1234), createdAt = 2))
            var oldQueryError: Exception? = null
            try {
                db.openHelper.writableDatabase.query("SELECT * FROM attachments ORDER BY createdAt DESC, id DESC").use {
                    while (it.moveToNext()) it.getBlob(it.getColumnIndexOrThrow("data"))
                }
            } catch (error: Exception) {
                oldQueryError = error
            }
            assertNotNull("Baseline query must reproduce the CursorWindow failure", oldQueryError)
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val dao = db.tripDao()
            assertEquals(trip, dao.getAllTrips().single())
            val summaries = dao.observeAttachments().first()
            assertEquals(2, summaries.size)
            assertEquals(original.size.toLong(), summaries.first { it.id == 1L }.sizeBytes)
            assertEquals(2, dao.getAttachmentsForTrip(1).size)
            val recovered = dao.getAllAttachments()
            assertArrayEquals(original, recovered.first { it.id == 1L }.data)
            val settings = SettingsRepository(context)
            val backup = BackupRepository(db, settings, SavedPlaceRepository(context), VehicleRepository(context))
            val json = backup.createBackupJson()
            val decoded = BackupCodec.decode(json)
            assertEquals(2, decoded.attachments.size)
            assertArrayEquals(original, decoded.attachments.first { it.id == 1L }.data)
            backup.restoreBackupJson(json)
            assertArrayEquals(original, dao.getAllAttachments().first { it.id == 1L }.data)
            val zipBytes = ByteArrayOutputStream()
            MonthPackageExporter.writeFromRepository(zipBytes, listOf(trip), YearMonth.of(2026, 10),
                AppSettings(), dao.getAttachmentSummaries(), dao::copyAttachment)
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(zipBytes.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                }
            }
            assertEquals(5, entries.size)
            assertTrue(entries.containsKey("stroski-2026-10.csv"))
            assertEquals(2, entries.keys.count { it.startsWith("priloge/") })
            assertTrue(entries.values.any { it.contentEquals(original) })
            File(context.getExternalFilesDir(null), "attachment-recovery-result.txt").writeText(
                "baselineFailure=$oldQueryError\ntripDistance=12345.0\nattachments=2\nlargeBytes=${original.size}\nreopen=PASS\nbackupRestoreBytes=PASS\nzipBothReceipts=PASS\n")
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
