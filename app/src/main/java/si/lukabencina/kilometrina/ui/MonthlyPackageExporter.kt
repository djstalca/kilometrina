package si.lukabencina.kilometrina.ui

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.YearMonth
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.TripAttachmentEntity
import si.lukabencina.kilometrina.data.TripEntity

object MonthlyPackageExporter {
    fun write(
        output: OutputStream,
        trips: List<TripEntity>,
        month: YearMonth,
        settings: AppSettings,
        attachments: List<TripAttachmentEntity>,
    ) {
        val businessTrips = trips.filter { it.endTime != null && it.tripType != "PRIVATE" }.sortedBy { it.startTime }
        ZipOutputStream(output.buffered()).use { zip ->
            val csv = "\uFEFF" + CsvExporter.build(businessTrips, month, settings)
            put(zip, "kilometrina-" + month + ".csv", csv.toByteArray(Charsets.UTF_8))

            val pdfBytes = ByteArrayOutputStream().use { pdf ->
                PdfReportExporter.write(pdf, businessTrips, month, settings)
                pdf.toByteArray()
            }
            put(zip, "kilometrina-" + month + ".pdf", pdfBytes)

            val tripIds = businessTrips.map { it.id }.toSet()
            attachments
                .filter { it.tripId in tripIds }
                .sortedWith(compareBy<TripAttachmentEntity> { it.tripId }.thenBy { it.addedAt })
                .forEach { attachment ->
                    val trip = businessTrips.firstOrNull { it.id == attachment.tripId }
                    val prefix = trip?.let { formatDate(it.startTime).replace(" ", "").replace(".", "-") } ?: "voznja"
                    val safeName = sanitizeFileName(attachment.displayName)
                    val bytes = Base64.getDecoder().decode(attachment.contentBase64)
                    put(zip, "priloge/" + prefix + "_" + attachment.tripId + "_" + safeName, bytes)
                }

            val summary = ReportCalculator.summarize(businessTrips)
            val info = buildString {
                appendLine("Kilometrina – mesečni paket " + month)
                appendLine("Voznik: " + settings.driverName.ifBlank { "—" })
                appendLine("Podjetje: " + settings.companyName.ifBlank { "—" })
                appendLine("Službene vožnje: " + summary.tripCount)
                appendLine("Kilometri: " + String.format(java.util.Locale.US, "%.1f", summary.distanceKm))
                appendLine("Skupaj: " + formatMoney(summary.totalAmount))
                appendLine("Priloge: " + attachments.count { it.tripId in tripIds })
                appendLine()
                appendLine("Zasebne vožnje niso vključene v obračun.")
            }
            put(zip, "POVZETEK.txt", info.toByteArray(Charsets.UTF_8))
        }
    }

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun sanitizeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .trim()
        .take(120)
        .ifBlank { "priloga" }
}
