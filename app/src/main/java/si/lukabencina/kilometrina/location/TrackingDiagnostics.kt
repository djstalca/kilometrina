package si.lukabencina.kilometrina.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GpsSignalQuality {
    Waiting,
    Good,
    Fair,
    Poor,
    Lost,
    Unavailable,
}

data class TrackingDiagnosticsState(
    val isTracking: Boolean = false,
    val locationAvailable: Boolean = true,
    val lastFixElapsedRealtimeMs: Long? = null,
    val lastAccuracyMeters: Float? = null,
    val acceptedSegments: Int = 0,
    val rejectedSegments: Int = 0,
    val lastRejectionReason: SegmentRejectionReason? = null,
)

object TrackingDiagnostics {
    private val mutableState = MutableStateFlow(TrackingDiagnosticsState())
    val state: StateFlow<TrackingDiagnosticsState> = mutableState.asStateFlow()

    fun trackingStarted() {
        mutableState.value = TrackingDiagnosticsState(isTracking = true)
    }

    fun trackingStopped() {
        mutableState.value = TrackingDiagnosticsState()
    }

    fun locationAvailabilityChanged(available: Boolean) {
        mutableState.value = mutableState.value.copy(locationAvailable = available)
    }

    fun locationFix(elapsedRealtimeMs: Long, accuracyMeters: Float) {
        mutableState.value = mutableState.value.copy(
            locationAvailable = true,
            lastFixElapsedRealtimeMs = elapsedRealtimeMs,
            lastAccuracyMeters = accuracyMeters,
        )
    }

    fun acceptedSegment() {
        val current = mutableState.value
        mutableState.value = current.copy(
            acceptedSegments = current.acceptedSegments + 1,
            lastRejectionReason = null,
        )
    }

    fun rejectedSegment(reason: SegmentRejectionReason) {
        val current = mutableState.value
        mutableState.value = current.copy(
            rejectedSegments = current.rejectedSegments + 1,
            lastRejectionReason = reason,
        )
    }
}

object GpsSignalEvaluator {
    fun quality(
        isTracking: Boolean,
        locationAvailable: Boolean,
        lastFixAgeSeconds: Double?,
        accuracyMeters: Float?,
    ): GpsSignalQuality {
        if (!isTracking) return GpsSignalQuality.Waiting
        if (!locationAvailable) return GpsSignalQuality.Unavailable
        if (lastFixAgeSeconds == null || accuracyMeters == null) return GpsSignalQuality.Waiting
        if (!lastFixAgeSeconds.isFinite() || lastFixAgeSeconds < 0.0) return GpsSignalQuality.Waiting
        if (lastFixAgeSeconds > 30.0) return GpsSignalQuality.Lost
        if (lastFixAgeSeconds > 15.0) return GpsSignalQuality.Poor
        return when {
            accuracyMeters <= 12f -> GpsSignalQuality.Good
            accuracyMeters <= 25f -> GpsSignalQuality.Fair
            else -> GpsSignalQuality.Poor
        }
    }
}
