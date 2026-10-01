package si.lukabencina.kilometrina.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.data.TripKinds

object PdfReportExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 24f
    private const val TABLE_TOP = 124f
    private const val HEADER_ROW_HEIGHT = 24f
    private const val TRIP_ROW_HEIGHT = 58f
    private const val TABLE_BOTTOM = 790f
    private const val SUMMARY_HEIGHT = 82f

    private val locale = Locale.forLanguageTag("sl-SI")
    private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    fun write(
        output: OutputStream,
        trips: List<TripEntity>,
        month: YearMonth,
        settings: AppSettings,
    ) {
        val completedTrips = trips.filter { it.endTime != null && it.tripKind != TripKinds.PRIVATE }.sortedBy { it.startTime }
        val summary = ReportCalculator.summarize(completedTrips)
        val vehicleSummary = completedTrips
            .map { listOf(it.vehicleName, it.registrationPlate).filter(String::isNotBlank).joinToString(" • ") }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(", ")
            .ifBlank {
                listOf(settings.vehicleName, settings.registrationPlate).filter(String::isNotBlank).joinToString(" • ").ifBlank { "—" }
            }
        val document = PdfDocument()
        try {
            var pageNumber = 1
            var rowIndex = 0
            var page = startPage(document, pageNumber)
            var canvas = page.canvas
            drawPageHeader(canvas, month, settings, vehicleSummary, pageNumber)
            var y = TABLE_TOP
            drawTableHeader(canvas, y)
            y += HEADER_ROW_HEIGHT

            for (trip in completedTrips) {
                if (y + TRIP_ROW_HEIGHT > TABLE_BOTTOM) {
                    drawPageFooter(canvas, pageNumber)
                    document.finishPage(page)
                    pageNumber += 1
                    page = startPage(document, pageNumber)
                    canvas = page.canvas
                    drawPageHeader(canvas, month, settings, vehicleSummary, pageNumber)
                    y = TABLE_TOP
                    drawTableHeader(canvas, y)
                    y += HEADER_ROW_HEIGHT
                }
                drawTripRow(canvas, y, trip, rowIndex)
                y += TRIP_ROW_HEIGHT
                rowIndex += 1
            }

            if (y + SUMMARY_HEIGHT > TABLE_BOTTOM) {
                drawPageFooter(canvas, pageNumber)
                document.finishPage(page)
                pageNumber += 1
                page = startPage(document, pageNumber)
                canvas = page.canvas
                drawPageHeader(canvas, month, settings, vehicleSummary, pageNumber)
                y = TABLE_TOP
            }

            drawSummary(canvas, y + 10f, summary)
            drawPageFooter(canvas, pageNumber)
            document.finishPage(page)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun startPage(document: PdfDocument, pageNumber: Int): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        return document.startPage(info)
    }

    private fun drawPageHeader(
        canvas: Canvas,
        month: YearMonth,
        settings: AppSettings,
        vehicleSummary: String,
        pageNumber: Int,
    ) {
        val titlePaint = textPaint(18f, bold = true, color = Color.rgb(24, 29, 36))
        val subtitlePaint = textPaint(9.5f, color = Color.rgb(80, 86, 96))
        val metaLabelPaint = textPaint(7.2f, bold = true, color = Color.rgb(94, 101, 112))
        val metaPaint = textPaint(8.3f, color = Color.rgb(32, 37, 45))

        canvas.drawText("Mesečni obračun kilometrine", MARGIN, 35f, titlePaint)
        val monthText = month.atDay(1).format(monthFormatter).replaceFirstChar { it.uppercase() }
        canvas.drawText(monthText, MARGIN, 52f, subtitlePaint)

        drawMeta(canvas, "VOZNIK", settings.driverName.ifBlank { "—" }, MARGIN, 72f, 250f, metaLabelPaint, metaPaint)
        drawMeta(canvas, "PODJETJE", settings.companyName.ifBlank { "—" }, 302f, 72f, 269f, metaLabelPaint, metaPaint)
        drawMeta(canvas, "VOZILA", vehicleSummary, MARGIN, 98f, PAGE_WIDTH - 2 * MARGIN, metaLabelPaint, metaPaint)

        val pagePaint = textPaint(7.2f, color = Color.rgb(110, 116, 126)).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Stran $pageNumber", PAGE_WIDTH - MARGIN, 35f, pagePaint)

        val line = Paint().apply {
            color = Color.rgb(218, 222, 228)
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN, 116f, PAGE_WIDTH - MARGIN, 116f, line)
    }

    private fun drawMeta(
        canvas: Canvas,
        label: String,
        value: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        labelPaint: Paint,
        valuePaint: Paint,
    ) {
        canvas.drawText(label, x, y, labelPaint)
        canvas.drawText(fitText(value, maxWidth, valuePaint), x, y + 13f, valuePaint)
    }

    private data class Column(val title: String, val width: Float, val align: Paint.Align = Paint.Align.LEFT)

    private val columns = listOf(
        Column("Datum", 60f),
        Column("Vožnja / relacija", 322f),
        Column("km", 55f, Paint.Align.RIGHT),
        Column("Skupaj", 110f, Paint.Align.RIGHT),
    )

    private fun drawTableHeader(canvas: Canvas, y: Float) {
        val background = Paint().apply { color = Color.rgb(239, 242, 246) }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + HEADER_ROW_HEIGHT, background)
        val paint = textPaint(7.2f, bold = true, color = Color.rgb(49, 56, 66))
        var x = MARGIN
        for (column in columns) {
            paint.textAlign = column.align
            val textX = if (column.align == Paint.Align.RIGHT) x + column.width - 6f else x + 6f
            canvas.drawText(column.title, textX, y + 16f, paint)
            x += column.width
        }
    }

    private fun drawTripRow(canvas: Canvas, y: Float, trip: TripEntity, rowIndex: Int) {
        if (rowIndex % 2 == 1) {
            val background = Paint().apply { color = Color.rgb(249, 250, 252) }
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + TRIP_ROW_HEIGHT, background)
        }

        val datePaint = textPaint(7.3f, color = Color.rgb(55, 61, 70))
        val titlePaint = textPaint(8.2f, bold = true, color = Color.rgb(32, 37, 45))
        val routePaint = textPaint(7.2f, color = Color.rgb(45, 51, 60))
        val vehiclePaint = textPaint(6.8f, color = Color.rgb(76, 83, 93))
        val costPaint = textPaint(7.0f, color = Color.rgb(68, 75, 85))
        val numberPaint = textPaint(8.0f, bold = true, color = Color.rgb(32, 37, 45)).apply {
            textAlign = Paint.Align.RIGHT
        }

        val dateX = MARGIN + 6f
        val detailsX = MARGIN + columns[0].width + 6f
        val detailsWidth = columns[1].width - 12f
        val kmRight = MARGIN + columns[0].width + columns[1].width + columns[2].width - 6f
        val totalRight = PAGE_WIDTH - MARGIN - 6f

        canvas.drawText(formatDate(trip.startTime), dateX, y + 19f, datePaint)
        canvas.drawText(
            "${formatTime(trip.startTime)}–${trip.endTime?.let(::formatTime).orEmpty()}",
            dateX,
            y + 33f,
            datePaint,
        )

        val title = trip.description.trim().ifBlank { trip.purpose }
        val route = formatTripRoute(trip)
        val vehicle = listOf(trip.vehicleName, trip.registrationPlate)
            .filter(String::isNotBlank)
            .joinToString(" • ")
            .ifBlank { "—" }
        val vehicleMeta = "Vozilo: $vehicle"

        val mileageAmount = tripCompensation(trip)
        val costs = buildList {
            if (mileageAmount > 0.0 || (trip.parkingCents <= 0 && trip.tollsCents <= 0)) {
                add("Kilometrina ${money(mileageAmount)}")
            }
            if (trip.parkingCents > 0) add("Parkirnina ${money(trip.parkingCents / 100.0)}")
            if (trip.tollsCents > 0) add("Cestnina ${money(trip.tollsCents / 100.0)}")
        }.joinToString(" • ")

        canvas.drawText(fitText(title, detailsWidth, titlePaint), detailsX, y + 13f, titlePaint)
        canvas.drawText(fitText(route, detailsWidth, routePaint), detailsX, y + 27f, routePaint)
        canvas.drawText(fitText(vehicleMeta, detailsWidth, vehiclePaint), detailsX, y + 40f, vehiclePaint)
        canvas.drawText(fitText(costs, detailsWidth, costPaint), detailsX, y + 53f, costPaint)

        canvas.drawText(number(trip.distanceMeters / 1000.0, 1), kmRight, y + 31f, numberPaint)
        canvas.drawText(money(tripTotalCost(trip)), totalRight, y + 31f, numberPaint)

        val line = Paint().apply {
            color = Color.rgb(229, 232, 237)
            strokeWidth = 0.7f
        }
        canvas.drawLine(MARGIN, y + TRIP_ROW_HEIGHT, PAGE_WIDTH - MARGIN, y + TRIP_ROW_HEIGHT, line)
    }

    private fun drawSummary(canvas: Canvas, y: Float, summary: ReportSummary) {
        val labelPaint = textPaint(7.2f, color = Color.rgb(93, 100, 111))
        val valuePaint = textPaint(9.5f, bold = true, color = Color.rgb(29, 34, 42)).apply {
            textAlign = Paint.Align.RIGHT
        }
        val totalLabelPaint = textPaint(7.2f, bold = true, color = Color.rgb(49, 56, 66))
        val titlePaint = textPaint(9.4f, bold = true, color = Color.rgb(31, 36, 44))
        canvas.drawText("Povzetek", MARGIN, y, titlePaint)

        val items = listOf(
            "Vožnje" to summary.tripCount.toString(),
            "Kilometri" to "${number(summary.distanceKm, 1)} km",
            "Kilometrina" to money(summary.mileageAmount),
            "Parkirnine" to money(summary.parkingAmount),
            "Cestnine" to money(summary.tollsAmount),
            "SKUPAJ" to money(summary.totalAmount),
        )
        val boxWidth = (PAGE_WIDTH - 2 * MARGIN) / 3f
        items.forEachIndexed { index, item ->
            val column = index % 3
            val row = index / 3
            val x = MARGIN + column * boxWidth
            val rowY = y + 18f + row * 31f
            val label = if (item.first == "SKUPAJ") totalLabelPaint else labelPaint
            canvas.drawText(item.first, x, rowY, label)
            canvas.drawText(item.second, x + boxWidth - 8f, rowY + 13f, valuePaint)
        }
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int) {
        val line = Paint().apply {
            color = Color.rgb(218, 222, 228)
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 35f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 35f, line)
        val paint = textPaint(7.5f, color = Color.rgb(117, 123, 132))
        canvas.drawText("Kilometrina • lokalno ustvarjeno poročilo", MARGIN, PAGE_HEIGHT - 20f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("$pageNumber", PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 20f, paint)
    }

    private val regularTypeface: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
    private val mediumTypeface: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    private fun textPaint(size: Float, bold: Boolean = false, color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) mediumTypeface else regularTypeface
            isFakeBoldText = false
            textScaleX = 1f
        }

    private fun fitText(value: String, maxWidth: Float, paint: Paint): String {
        val clean = value.replace('\n', ' ').replace('\r', ' ').trim()
        if (paint.measureText(clean) <= maxWidth) return clean
        val ellipsis = "…"
        var end = clean.length
        while (end > 0 && paint.measureText(clean.substring(0, end) + ellipsis) > maxWidth) end -= 1
        return if (end > 0) clean.substring(0, end).trimEnd() + ellipsis else ellipsis
    }

    private fun number(value: Double, decimals: Int): String = String.format(locale, "%.${decimals}f", value)
    private fun money(value: Double): String = String.format(locale, "%.2f €", value)
}
