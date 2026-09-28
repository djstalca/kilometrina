package si.lukabencina.kilometrina.data

enum class TripGpsQuality { Good, Review, NoGps }

data class TripGpsAssessment(
    val quality: TripGpsQuality,
    val message: String,
    val averageAccuracyMeters: Int? = null,
    val longestGapSeconds: Long? = null,
)

object TripGpsQualityEvaluator {
    fun evaluate(points: List<LocationPointEntity>): TripGpsAssessment {
        if (points.size < 2) {
            return TripGpsAssessment(TripGpsQuality.NoGps, "GPS trasa ni na voljo.")
        }
        val sorted = points.sortedBy { it.timestamp }
        val averageAccuracy = sorted.map { it.accuracyMeters.toDouble() }.average()
        val longestGap = sorted.zipWithNext { a, b ->
            ((b.timestamp - a.timestamp).coerceAtLeast(0L) / 1000L)
        }.maxOrNull() ?: 0L
        val poorRatio = sorted.count { it.accuracyMeters > 35f }.toDouble() / sorted.size
        val needsReview = averageAccuracy > 25.0 || longestGap > 90L || poorRatio > 0.20
        return if (needsReview) {
            TripGpsAssessment(
                TripGpsQuality.Review,
                "GPS zapis je smiselno preveriti pred oddajo.",
                averageAccuracy.toInt(),
                longestGap,
            )
        } else {
            TripGpsAssessment(
                TripGpsQuality.Good,
                "GPS zapis je videti dober.",
                averageAccuracy.toInt(),
                longestGap,
            )
        }
    }
}
