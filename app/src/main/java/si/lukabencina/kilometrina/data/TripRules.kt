package si.lukabencina.kilometrina.data

object TripRules {
    fun hasValidTimeRange(startTime: Long, endTime: Long): Boolean = endTime > startTime

    fun isValidRate(rate: Double): Boolean = rate.isFinite() && rate in 0.0..10.0

    fun areValidNumbers(km: Double, rate: Double, parking: Double, tolls: Double): Boolean =
        km.isFinite() && km >= 0.0 && (km * 1000.0).isFinite() &&
            isValidRate(rate) && (km * rate).isFinite() &&
            listOf(parking, tolls).all { it.isFinite() && it >= 0.0 && it <= Int.MAX_VALUE / 100.0 }

    fun requireValidTripNumbers(trip: TripEntity) {
        require(areValidNumbers(trip.distanceMeters / 1000.0, trip.ratePerKm,
            trip.parkingCents / 100.0, trip.tollsCents / 100.0)) { "Preveri razdaljo, postavko in dodatne stroške." }
    }
}
