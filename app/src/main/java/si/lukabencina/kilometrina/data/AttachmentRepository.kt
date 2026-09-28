package si.lukabencina.kilometrina.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AttachmentRepository(
    private val context: Context,
    private val dao: TripDao,
) {
    private val root: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }

    suspend fun listForTrip(tripId: Long): List<TripAttachmentEntity> = dao.getAttachmentsForTrip(tripId)
    fun fileFor(attachment: TripAttachmentEntity): File = File(root, attachment.storedFileName)

    suspend fun addFromUri(tripId: Long, uri: Uri): TripAttachmentEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "priloga"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) displayName = cursor.getString(index).orEmpty().ifBlank { displayName }
            }
        }
        val cleanName = displayName.replace(Regex("[\\r\\n]"), " ").take(180)
        val extension = cleanName.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{1,12}")) }
        val storedName = UUID.randomUUID().toString() + (extension?.let { ".$it" } ?: "")
        val target = File(root, storedName)
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) }
            ?: error("Priloge ni bilo mogoče odpreti.")
        val size = target.length()
        if (size > 50L * 1024 * 1024) {
            target.delete()
            error("Posamezna priloga je lahko velika največ 50 MB.")
        }
        val entity = TripAttachmentEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            displayName = cleanName,
            mimeType = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }.take(120),
            storedFileName = storedName,
            sizeBytes = size,
            createdAt = System.currentTimeMillis(),
        )
        dao.insertAttachment(entity)
        entity
    }

    suspend fun delete(attachment: TripAttachmentEntity) = withContext(Dispatchers.IO) {
        dao.deleteAttachment(attachment.id)
        fileFor(attachment).delete()
    }

    suspend fun deleteForTrip(tripId: Long) = withContext(Dispatchers.IO) {
        dao.getAttachmentsForTrip(tripId).forEach { fileFor(it).delete() }
    }

    suspend fun replaceAll(metadata: List<TripAttachmentEntity>, sourceDirectory: File) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "attachments-restore-${System.nanoTime()}").apply { mkdirs() }
        metadata.forEach { item ->
            val source = File(sourceDirectory, item.storedFileName)
            require(source.isFile) { "V backupu manjka priloga ${item.displayName}." }
            require(source.length() == item.sizeBytes) { "Velikost priloge ${item.displayName} se ne ujema." }
            source.copyTo(File(staging, item.storedFileName), overwrite = true)
        }
        val current = root
        val old = File(context.cacheDir, "attachments-old-${System.nanoTime()}")
        if (current.exists() && !current.renameTo(old)) error("Obstoječih prilog ni bilo mogoče začasno premakniti.")
        try {
            if (!staging.renameTo(current)) {
                current.mkdirs()
                staging.listFiles().orEmpty().forEach { it.copyTo(File(current, it.name), overwrite = true) }
                staging.deleteRecursively()
            }
            old.deleteRecursively()
        } catch (error: Throwable) {
            current.deleteRecursively()
            if (old.exists()) old.renameTo(current)
            throw error
        }
    }

    suspend fun clearFiles() = withContext(Dispatchers.IO) {
        root.deleteRecursively()
        root.mkdirs()
    }
}
