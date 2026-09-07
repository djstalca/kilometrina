package si.lukabencina.kilometrina.data

object TripRules {
    fun hasValidTimeRange(startTime: Long, endTime: Long): Boolean = endTime > startTime
}
