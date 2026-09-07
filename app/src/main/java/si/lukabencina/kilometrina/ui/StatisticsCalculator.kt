package si.lukabencina.kilometrina.ui

import java.time.Instant
import java.time.Year
import java.time.ZoneId
import si.lukabencina.kilometrina.data.TripEntity

data class MonthlyStatistics(
    val month: Int,
    val tripCount: Int,
    val distanceKm: Double,
    val totalAmount: Double,
)

data class RouteStatistics(
    val route: String,
    val tripCount: Int,
    val distanceKm: Double,
    val totalAmount: Double,
)

data class VehicleStatistics(
    val vehicle: String,
    val tripCount: Int,
    val distanceKm: Double,
    val totalAmount: Double,
)

data class YearStatistics(
    val year: Int,
    val tripCount: Int,
    val distanceKm: Double,
    val mileageAmount: Double,
    val additionalCosts: Double,
    val totalAmount: Double,
    val months: List<MonthlyStatistics>,
    val topRoutes: List<RouteStatistics>,
    val vehicles: List<VehicleStatistics>,
)

object StatisticsCalculator {
    fun calculate(
        trips: List<TripEntity>,
        year: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): YearStatistics {
        val completed = trips.filter { trip ->
            trip.endTime != null && Year.from(Instant.ofEpochMilli(trip.startTime).atZone(zoneId)) == Year.of(year)
        }

        val months = (1..12).map { month ->
            val monthTrips = completed.filter {
                Instant.ofEpochMilli(it.startTime).atZone(zoneId).monthValue == month
            }
            MonthlyStatistics(
                month = month,
                tripCount = monthTrips.size,
                distanceKm = monthTrips.sumOf { it.distanceMeters } / 1000.0,
                totalAmount = monthTrips.sumOf(::tripTotalCost),
            )
        }

        val topRoutes = completed
            .groupBy { "${cleanLocation(it.startAddress)} → ${cleanLocation(it.endAddress)}" }
            .map { (route, routeTrips) ->
                RouteStatistics(
                    route = route,
                    tripCount = routeTrips.size,
                    distanceKm = routeTrips.sumOf { it.distanceMeters } / 1000.0,
                    totalAmount = routeTrips.sumOf(::tripTotalCost),
                )
            }
            .sortedWith(compareByDescending<RouteStatistics> { it.tripCount }.thenByDescending { it.distanceKm })
            .take(5)

        val vehicles = completed
            .groupBy { trip ->
                listOf(trip.vehicleName, trip.registrationPlate)
                    .filter(String::isNotBlank)
                    .joinToString(" • ")
                    .ifBlank { "Brez vozila" }
            }
            .map { (vehicle, vehicleTrips) ->
                VehicleStatistics(
                    vehicle = vehicle,
                    tripCount = vehicleTrips.size,
                    distanceKm = vehicleTrips.sumOf { it.distanceMeters } / 1000.0,
                    totalAmount = vehicleTrips.sumOf(::tripTotalCost),
                )
            }
            .sortedByDescending { it.distanceKm }

        return YearStatistics(
            year = year,
            tripCount = completed.size,
            distanceKm = completed.sumOf { it.distanceMeters } / 1000.0,
            mileageAmount = completed.sumOf(::tripCompensation),
            additionalCosts = completed.sumOf(::tripAdditionalCosts),
            totalAmount = completed.sumOf(::tripTotalCost),
            months = months,
            topRoutes = topRoutes,
            vehicles = vehicles,
        )
    }

    private fun cleanLocation(value: String?): String = value
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Lokacija ni na voljo"
}
