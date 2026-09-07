package si.lukabencina.kilometrina.location

object TrackingMath {
    fun shouldAcceptSegment(
        distanceMeters: Double,
        elapsedSeconds: Double,
        accuracyMeters: Float,
    ): Boolean {
        if (!distanceMeters.isFinite() || !elapsedSeconds.isFinite()) return false
        if (distanceMeters < 4.0 || elapsedSeconds <= 0.0) return false
        if (accuracyMeters > 40f) return false
        val impliedSpeedMetersPerSecond = distanceMeters / elapsedSeconds
        return impliedSpeedMetersPerSecond <= 70.0
    }
}
