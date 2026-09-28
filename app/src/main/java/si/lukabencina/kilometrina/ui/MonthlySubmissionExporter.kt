package si.lukabencina.kilometrina.ui

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.TripEntity

object MonthlySubmissionExporter {
    suspend fun createShareUri(
        context: Context,
        trips: List<TripEntity>,
        month: YearMonth,
        settings: AppSettings,
    ): Uri = withContext(Dispatchers.IO) {
        val businessTrips = trips.filter { it.endTime != null && it.isBusiness }.sortedBy { it.startTime }
        require(businessTrips.isNotEmpty()) { "V izbranem mesecu ni službenih voženj za oddajo." }

        val app = context.applicationContext as KilometrinaApplication
        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        shareDir.listFiles()?.filter { it.name.startsWith("kilometrina-") }?.forEach(File::delete)
        val outputFile = File(shareDir, "kilometrina-${month}.zip")

        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("kilometrina-${month}.csv"))
            zip.write("\uFEFF".toByteArray(Charsets.UTF_8))
            zip.write(CsvExporter.build(businessTrips, month, settings).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val pdf = ByteArrayOutputStream()
            PdfReportExporter.write(pdf, businessTrips, month, settings)
            zip.putNextEntry(ZipEntry("kilometrina-${month}.pdf"))
            zip.write(pdf.toByteArray())
            zip.closeEntry()

            businessTrips.forEach { trip ->
                app.attachmentRepository.listForTrip(trip.id).forEach { attachment ->
                    val file = app.attachmentRepository.fileFor(attachment)
                    if (file.isFile) {
                        val safeName = attachment.displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120)
                        zip.putNextEntry(ZipEntry("priloge/${formatDate(trip.startTime)}-${trip.id}-${attachment.id.take(8)}-$safeName"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }

        FileProvider.getUriForFile(context, "${context.packageName}.files", outputFile)
    }
}
