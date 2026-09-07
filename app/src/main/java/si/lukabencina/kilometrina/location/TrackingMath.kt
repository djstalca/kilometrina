package si.lukabencina.kilometrina.location

import kotlin.math.max

enum class SegmentRejectionReason {
    InvalidMeasurement,
    StaleFix,
    LowAccuracy,
    GpsJitter,
    ImpossibleSpeed,
}

data class SegmentEvaluation(
    val accepted: Boolean,
    val rejectionReason: SegmentRejectionReason? = null,
)

object TrackingMath {
    fun evaluateSegment(
        distanceMeters: Double,
        elapsedSeconds: Double,
        accuracyMeters: Float,
        previousAccuracyMeters: Float = accuracyMeters,
        fixAgeSeconds: Double = 0.0,
    ): SegmentEvaluation {
        if (
            !distanceMeters.isFinite() ||
            !elapsedSeconds.isFinite() ||
            !fixAgeSeconds.isFinite() ||
            elapsedSeconds <= 0.0 ||
            distanceMeters < 0.0
        ) {
            return SegmentEvaluation(false, SegmentRejectionReason.InvalidMeasurement)
        }
        if (fixAgeSeconds > 30.0) {
            return SegmentEvaluation(false, SegmentRejectionReason.StaleFix)
        }
        if (accuracyMeters > 50f || previousAccuracyMeters > 50f) {
            return SegmentEvaluation(false, SegmentRejectionReason.LowAccuracy)
        }

        // A fixed 4 m threshold still accumulates GPS drift when the reported
        // accuracy is 10–30 m. Scale the jitter gate with the weaker of the
        // two fixes while keeping 4 m as the absolute minimum.
        val jitterThresholdMeters = max(
            4.0,
            max(accuracyMeters, previousAccuracyMeters).toDouble() * 0.6,
        )
        if (distanceMeters < jitterThresholdMeters) {
            return SegmentEvaluation(false, SegmentRejectionReason.GpsJitter)
        }

        val impliedSpeedMetersPerSecond = distanceMeters / elapsedSeconds
        if (impliedSpeedMetersPerSecond > 70.0) {
            return SegmentEvaluation(false, SegmentRejectionReason.ImpossibleSpeed)
        }
        return SegmentEvaluation(true)
    }

    fun shouldAcceptSegment(
        distanceMeters: Double,
        elapsedSeconds: Double,
        accuracyMeters: Float,
    ): Boolean = evaluateSegment(
        distanceMeters = distanceMeters,
        elapsedSeconds = elapsedSeconds,
        accuracyMeters = accuracyMeters,
    ).accepted
}
