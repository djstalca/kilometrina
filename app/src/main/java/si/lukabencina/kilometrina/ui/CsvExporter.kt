package si.lukabencina.kilometrina.ui

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.TripEntity

object CsvExporter {
    private val locale = Locale.forLanguageTag("sl-SI")
    private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    fun build(trips: List<TripEntity>): String = build(trips, null, AppSettings())

    fun build(
        trips: List<TripEntity>,
        month: YearMonth?,
        settings: AppSettings,
    ): String {
        val completed = trips.filter { it.endTime != null }.sortedBy { it.startTime }
        val summary = ReportCalculator.summarize(completed)
        val lines = mutableListOf<String>()

        lines += metadata("Obračun", "Mesečni obračun kilometrine")
        month?.let {
            val value = it.atDay(1).format(monthFormatter).replaceFirstChar { char -> char.uppercase(locale) }
            lines += metadata("Mesec", value)
        }
        lines += metadata("Voznik", settings.driverName)
        lines += metadata("Podjetje", settings.companyName)
        lines += metadata("Vozilo", settings.vehicleName)
        lines += metadata("Registrska oznaka", settings.registrationPlate)
        lines += ""

        lines += listOf(
            "Datum",
            "Čas odhoda",
            "Čas prihoda",
            "Odhod",
            "Prihod",
            "Namen",
            "Kilometri",
            "Postavka EUR/km",
            "Kilometrina EUR",
            "Parkirnina EUR",
            "Cestnina EUR",
            "Skupaj EUR",
        ).joinToString(";")

        completed.forEach { trip ->
            lines += listOf(
                csv(formatDate(trip.startTime)),
                csv(formatTime(trip.startTime)),
                csv(trip.endTime?.let(::formatTime).orEmpty()),
                csv(trip.startAddress),
                csv(trip.endAddress.orEmpty()),
                csv(trip.purpose),
                decimal(trip.distanceMeters / 1000.0),
                decimal(trip.ratePerKm),
                decimal(tripCompensation(trip)),
                decimal(trip.parkingCents / 100.0),
                decimal(trip.tollsCents / 100.0),
                decimal(tripTotalCost(trip)),
            ).joinToString(";")
        }

        lines += ""
        lines += metadata("Število voženj", summary.tripCount.toString())
        lines += metadata("Kilometri skupaj", decimal(summary.distanceKm))
        lines += metadata("Kilometrina skupaj EUR", decimal(summary.mileageAmount))
        lines += metadata("Parkirnine skupaj EUR", decimal(summary.parkingAmount))
        lines += metadata("Cestnine skupaj EUR", decimal(summary.tollsAmount))
        lines += metadata("SKUPAJ EUR", decimal(summary.totalAmount))

        return lines.joinToString("\n")
    }

    private fun metadata(label: String, value: String): String = "${csv(label)};${csv(value)}"
    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun decimal(value: Double): String = String.format(Locale.US, "%.2f", value).replace('.', ',')
}
