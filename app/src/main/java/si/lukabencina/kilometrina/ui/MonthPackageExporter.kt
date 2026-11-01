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
import si.lukabencina.kilometrina.data.AttachmentSummary
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.data.TripKinds
import si.lukabencina.kilometrina.data.ExpenseEntity
import si.lukabencina.kilometrina.data.ExpenseRules
import si.lukabencina.kilometrina.data.ExpenseKinds

object MonthPackageExporter {
    fun write(
        output: OutputStream,
        trips: List<TripEntity>,
        month: YearMonth,
        settings: AppSettings,
        attachments: List<AttachmentEntity>,
        expenses: List<ExpenseEntity> = ExpenseRules.legacy(trips),
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

            val receiptPaths = mutableMapOf<Long, String>()
            attachments
                .filter { it.tripId in validTripIds }
                .sortedWith(compareBy<AttachmentEntity> { it.tripId }.thenBy { it.createdAt }.thenBy { it.id })
                .forEachIndexed { index, attachment ->
                    val trip = monthTrips.firstOrNull { it.id == attachment.tripId }
                    val date = trip?.let { formatDate(it.startTime).replace(" ", "").replace(".", "-") } ?: "voznja"
                    val safeName = sanitizeFileName(attachment.displayName).ifBlank { "priloga-${index + 1}" }
                    val path = "priloge/$date-${attachment.tripId}-${index + 1}-$safeName"
                    receiptPaths[attachment.id] = path
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(attachment.data)
                    zip.closeEntry()
                }
            writeExpenseList(zip, month, monthTrips, expenses, receiptPaths)
        }
    }

    suspend fun writeFromRepository(
        output: OutputStream,
        trips: List<TripEntity>,
        month: YearMonth,
        settings: AppSettings,
        attachments: List<AttachmentSummary>,
        copyAttachment: suspend (AttachmentSummary, OutputStream) -> Unit,
        expenses: List<ExpenseEntity> = ExpenseRules.legacy(trips),
    ) {
        val monthTrips = trips.filter {
            it.endTime != null && it.tripKind != TripKinds.PRIVATE &&
                YearMonth.from(Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault())) == month
        }.sortedBy { it.startTime }
        val validIds = monthTrips.map { it.id }.toSet()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("kilometrina-$month.csv"))
            zip.write("\uFEFF".toByteArray(Charsets.UTF_8))
            zip.write(CsvExporter.build(monthTrips, month, settings).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("kilometrina-$month.pdf"))
            PdfReportExporter.write(zip, monthTrips, month, settings)
            zip.closeEntry()
            val receiptPaths = mutableMapOf<Long, String>()
            attachments.filter { it.tripId in validIds }
                .sortedWith(compareBy<AttachmentSummary> { it.tripId }.thenBy { it.createdAt }.thenBy { it.id })
                .forEachIndexed { index, attachment ->
                    val trip = monthTrips.first { it.id == attachment.tripId }
                    val date = formatDate(trip.startTime).replace(" ", "").replace(".", "-")
                    val safeName = sanitizeFileName(attachment.displayName).ifBlank { "priloga" }
                    val path = "priloge/$date-${attachment.tripId}-${index + 1}-$safeName"
                    receiptPaths[attachment.id] = path
                    zip.putNextEntry(ZipEntry(path))
                    copyAttachment(attachment, zip)
                    zip.closeEntry()
                }
            writeExpenseList(zip, month, monthTrips, expenses, receiptPaths)
        }
    }

    private fun writeExpenseList(zip: ZipOutputStream, month: YearMonth, trips: List<TripEntity>,
        expenses: List<ExpenseEntity>, receiptPaths: Map<Long, String>) {
        val byId = trips.associateBy { it.id }
        fun cell(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
        val csv = buildString {
            append("\uFEFFDatum vožnje;ID vožnje;ID stroška;Vrsta;Znesek EUR;Opomba;Račun\n")
            expenses.filter { it.tripId in byId }.sortedWith(compareBy<ExpenseEntity> { it.tripId }.thenBy { it.createdAt }.thenBy { it.id }).forEach {
                append(listOf(formatDate(byId.getValue(it.tripId).startTime), it.tripId.toString(), it.id,
                    ExpenseKinds.label(it.kind), java.math.BigDecimal.valueOf(it.amountCents.toLong(), 2).toPlainString().replace('.', ','),
                    it.note, receiptPaths[it.attachmentId].orEmpty()).joinToString(";", transform = ::cell))
                append('\n')
            }
        }
        zip.putNextEntry(ZipEntry("stroski-$month.csv"))
        zip.write(csv.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120)
}
