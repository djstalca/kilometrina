package si.lukabencina.kilometrina.ui

import si.lukabencina.kilometrina.data.TripEntity
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val slLocale = Locale.forLanguageTag("sl-SI")
private val dateFormatter = DateTimeFormatter.ofPattern("d. M. yyyy", slLocale)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", slLocale)

fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateFormatter)

fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter)

fun formatKm(meters: Double, decimals: Int = 1): String =
    String.format(slLocale, "%.${decimals}f km", meters / 1000.0)

fun formatMoney(amount: Double): String = NumberFormat.getCurrencyInstance(slLocale).format(amount)

fun tripCompensation(trip: TripEntity): Double = (trip.distanceMeters / 1000.0) * trip.ratePerKm

fun shortLocation(address: String?): String {
    if (address.isNullOrBlank()) return "Lokacija ni na voljo"
    return address.split(',').take(2).joinToString(", ").trim()
}
