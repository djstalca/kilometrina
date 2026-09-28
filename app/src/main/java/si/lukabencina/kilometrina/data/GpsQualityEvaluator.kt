package si.lukabencina.kilometrina.data

object GpsQuality {
    const val GOOD = "GOOD"
    const val FAIR = "FAIR"
    const val POOR = "POOR"
    const val MISSING = "MISSING"
    const val MANUAL = "MANUAL"
}

object GpsQualityEvaluator {
    fun evaluate(points: List<LocationPointEntity>): String {
        if (points.size < 2) return GpsQuality.MISSING
        val sorted = points.sortedBy { it.timestamp }
        val averageAccuracy = sorted.map { it.accuracyMeters.toDouble() }.average()
        val maxGapSeconds = sorted.zipWithNext()
            .maxOfOrNull { (a, b) -> ((b.timestamp - a.timestamp).coerceAtLeast(0L)) / 1000.0 }
            ?: 0.0
        return when {
            averageAccuracy <= 15.0 && maxGapSeconds <= 30.0 -> GpsQuality.GOOD
            averageAccuracy <= 30.0 && maxGapSeconds <= 60.0 -> GpsQuality.FAIR
            else -> GpsQuality.POOR
        }
    }
}
