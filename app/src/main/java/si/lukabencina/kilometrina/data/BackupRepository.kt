package si.lukabencina.kilometrina.data

import androidx.room.withTransaction
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first

class BackupRepository(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val savedPlaceRepository: SavedPlaceRepository,
    private val vehicleRepository: VehicleRepository,
    private val attachmentRepository: AttachmentRepository,
) {
    private val dao = database.tripDao()

    suspend fun createBackupJson(): String {
        check(dao.getActiveTrip() == null) { "Najprej zaključi aktivno vožnjo." }
        return BackupCodec.encode(snapshot())
    }

    suspend fun writeBackup(output: OutputStream) {
        check(dao.getActiveTrip() == null) { "Najprej zaključi aktivno vožnjo." }
        val data = snapshot()
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(BackupCodec.encode(data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            data.attachments.forEach { attachment ->
                val file = attachmentRepository.fileFor(attachment)
                require(file.isFile) { "Manjka datoteka priloge ${attachment.displayName}." }
                zip.putNextEntry(ZipEntry("attachments/${attachment.storedFileName}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun restoreBackup(input: InputStream): BackupRestoreSummary {
        check(dao.getActiveTrip() == null) { "Obnovitev med aktivno vožnjo ni dovoljena." }
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val signature = ByteArray(4)
        val count = buffered.read(signature)
        buffered.reset()
        val isZip = count == 4 && signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte()
        return if (isZip) restoreArchive(buffered) else restoreBackupJson(readTextLimited(buffered))
    }

    suspend fun restoreBackupJson(raw: String): BackupRestoreSummary {
        val incoming = BackupCodec.decode(raw)
        require(incoming.attachments.isEmpty()) {
            "Ta JSON vsebuje podatke o prilogah, ne pa datotek. Uporabi celotno .kmbackup kopijo."
        }
        return applyWithRollback(incoming, null)
    }

    private suspend fun restoreArchive(input: InputStream): BackupRestoreSummary {
        val tempRoot = File(database.openHelper.writableDatabase.path).parentFile
            ?: error("Začasne mape ni mogoče ustvariti.")
        val temp = File(tempRoot, "km-backup-${System.nanoTime()}").apply { mkdirs() }
        val attachmentDir = File(temp, "attachments").apply { mkdirs() }
        var json: String? = null
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.name.contains("..") && !entry.name.startsWith("/")) { "Neveljavna pot v backupu." }
                if (!entry.isDirectory) {
                    when {
                        entry.name == "backup.json" -> {
                            val out = ByteArrayOutputStream()
                            zip.copyToLimited(out, 64L * 1024 * 1024)
                            json = out.toString(Charsets.UTF_8.name())
                        }
                        entry.name.startsWith("attachments/") -> {
                            val fileName = entry.name.removePrefix("attachments/")
                            require(fileName.matches(Regex("[A-Za-z0-9._-]{1,160}"))) { "Neveljavno ime priloge v backupu." }
                            File(attachmentDir, fileName).outputStream().use { out ->
                                totalBytes += zip.copyToLimited(out, 50L * 1024 * 1024)
                                require(totalBytes <= 250L * 1024 * 1024) { "Varnostna kopija prilog je prevelika." }
                            }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        try {
            val incoming = BackupCodec.decode(json ?: error("Backup ne vsebuje backup.json."))
            require(incoming.attachments.all { File(attachmentDir, it.storedFileName).isFile }) {
                "Backup ne vsebuje vseh datotek prilog."
            }
            return applyWithRollback(incoming, attachmentDir)
        } finally {
            temp.deleteRecursively()
        }
    }

    private suspend fun applyWithRollback(data: BackupData, attachmentSource: File?): BackupRestoreSummary {
        val previous = snapshot()
        val tempRoot = File(database.openHelper.writableDatabase.path).parentFile
            ?: error("Začasne mape ni mogoče ustvariti.")
        val previousDir = File(tempRoot, "km-attachments-${System.nanoTime()}").apply { mkdirs() }
        previous.attachments.forEach { item ->
            val file = attachmentRepository.fileFor(item)
            if (file.isFile) file.copyTo(File(previousDir, item.storedFileName), overwrite = true)
        }
        try {
            apply(data, attachmentSource)
        } catch (error: Throwable) {
            runCatching { apply(previous, previousDir) }
            throw error
        } finally {
            previousDir.deleteRecursively()
        }
        return BackupRestoreSummary(
            tripCount = data.trips.size,
            pointCount = data.points.size,
            savedPlaceCount = data.savedPlaces.size,
            vehicleCount = data.vehicles.vehicles.size,
            attachmentCount = data.attachments.size,
        )
    }

    private suspend fun snapshot(): BackupData = BackupData(
        generatedAt = System.currentTimeMillis(),
        settings = settingsRepository.settings.first(),
        savedPlaces = savedPlaceRepository.places.first(),
        vehicles = vehicleRepository.state.first(),
        trips = dao.getAllTrips(),
        points = dao.getAllPoints(),
        attachments = dao.getAllAttachments(),
    )

    private suspend fun apply(data: BackupData, attachmentSource: File?) {
        if (data.attachments.isNotEmpty()) {
            requireNotNull(attachmentSource) { "Backup prilog nima pripadajočih datotek." }
            attachmentRepository.replaceAll(data.attachments, attachmentSource)
        } else {
            attachmentRepository.clearFiles()
        }
        database.withTransaction {
            dao.deleteAllAttachments()
            dao.deleteAllPoints()
            dao.deleteAllTrips()
            if (data.trips.isNotEmpty()) dao.insertTrips(data.trips)
            if (data.points.isNotEmpty()) dao.insertPoints(data.points)
            if (data.attachments.isNotEmpty()) dao.insertAttachments(data.attachments)
        }
        settingsRepository.replaceAll(data.settings)
        savedPlaceRepository.replaceAll(data.savedPlaces)
        vehicleRepository.replaceAll(data.vehicles)
    }

    private fun readTextLimited(input: InputStream): String {
        val output = ByteArrayOutputStream()
        input.copyToLimited(output, 64L * 1024 * 1024)
        return output.toString(Charsets.UTF_8.name())
    }

    private fun InputStream.copyToLimited(output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Varnostna kopija je prevelika." }
            output.write(buffer, 0, read)
        }
        return total
    }
}

data class BackupRestoreSummary(
    val tripCount: Int,
    val pointCount: Int,
    val savedPlaceCount: Int,
    val vehicleCount: Int = 0,
    val attachmentCount: Int = 0,
)
