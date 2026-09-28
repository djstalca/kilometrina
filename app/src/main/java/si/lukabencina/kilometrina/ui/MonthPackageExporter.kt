package si.lukabencina.kilometrina.ui

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.AttachmentEntity
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.data.TripKinds

object MonthPackageExporter {
    fun write(
        output: OutputStream,
        trips: List<TripEntity>,
        month: YearMonth,
        settings: AppSettings,
        attachments: List<AttachmentEntity>,
    ) {
        val monthTrips = trips.filter { trip ->
            trip.endTime != null &&
                trip.tripKind != TripKinds.PRIVATE &&
                YearMonth.from(Instant.ofEpochMilli(trip.startTime).atZone(ZoneId.systemDefault())) == month
        }.sortedBy { it.startTime }
        val validTripIds = monthTrips.map { it.id }.toSet()

        ZipOutputStream(output).use { zip ->
            val csvName = "kilometrina-$month.csv"
            zip.putNextEntry(ZipEntry(csvName))
            zip.write("\uFEFF".toByteArray(Charsets.UTF_8))
            zip.write(CsvExporter.build(monthTrips, month, settings).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val pdfBytes = ByteArrayOutputStream().use { buffer ->
                PdfReportExporter.write(buffer, monthTrips, month, settings)
                buffer.toByteArray()
            }
            zip.putNextEntry(ZipEntry("kilometrina-$month.pdf"))
            zip.write(pdfBytes)
            zip.closeEntry()

            attachments
                .filter { it.tripId in validTripIds }
                .sortedWith(compareBy<AttachmentEntity> { it.tripId }.thenBy { it.createdAt }.thenBy { it.id })
                .forEachIndexed { index, attachment ->
                    val trip = monthTrips.firstOrNull { it.id == attachment.tripId }
                    val date = trip?.let { formatDate(it.startTime).replace(" ", "").replace(".", "-") } ?: "voznja"
                    val safeName = sanitizeFileName(attachment.displayName).ifBlank { "priloga-${index + 1}" }
                    zip.putNextEntry(ZipEntry("priloge/$date-${attachment.tripId}-$safeName"))
                    zip.write(attachment.data)
                    zip.closeEntry()
                }
        }
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120)
}
