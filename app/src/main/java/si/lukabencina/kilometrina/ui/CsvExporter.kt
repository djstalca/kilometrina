package si.lukabencina.kilometrina.ui

import si.lukabencina.kilometrina.data.TripEntity
import java.util.Locale

object CsvExporter {
    fun build(trips: List<TripEntity>): String {
        val header = listOf(
            "Datum",
            "Čas odhoda",
            "Čas prihoda",
            "Odhod",
            "Prihod",
            "Namen",
            "Kilometri",
            "Postavka EUR/km",
            "Kilometrina EUR",
        ).joinToString(";")

        val rows = trips.filter { it.endTime != null }.map { trip ->
            listOf(
                csv(formatDate(trip.startTime)),
                csv(formatTime(trip.startTime)),
                csv(trip.endTime?.let(::formatTime).orEmpty()),
                csv(trip.startAddress),
                csv(trip.endAddress.orEmpty()),
                csv(trip.purpose),
                decimal(trip.distanceMeters / 1000.0),
                decimal(trip.ratePerKm),
                decimal(tripCompensation(trip)),
            ).joinToString(";")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun decimal(value: Double): String = String.format(Locale.US, "%.2f", value).replace('.', ',')
}
