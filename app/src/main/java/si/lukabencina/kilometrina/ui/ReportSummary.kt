package si.lukabencina.kilometrina.ui

import si.lukabencina.kilometrina.data.TripEntity

data class ReportSummary(
    val tripCount: Int,
    val distanceKm: Double,
    val mileageAmount: Double,
    val parkingAmount: Double,
    val tollsAmount: Double,
    val totalAmount: Double,
)

object ReportCalculator {
    fun summarize(trips: List<TripEntity>): ReportSummary {
        val completed = trips.filter { it.endTime != null }
        val distanceKm = completed.sumOf { it.distanceMeters } / 1000.0
        val mileage = completed.sumOf(::tripCompensation)
        val parking = completed.sumOf { it.parkingCents / 100.0 }
        val tolls = completed.sumOf { it.tollsCents / 100.0 }
        return ReportSummary(
            tripCount = completed.size,
            distanceKm = distanceKm,
            mileageAmount = mileage,
            parkingAmount = parking,
            tollsAmount = tolls,
            totalAmount = mileage + parking + tolls,
        )
    }
}
