package si.lukabencina.kilometrina.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.MainActivity
import si.lukabencina.kilometrina.R
import java.util.Locale

class LocationTrackingService : Service() {
    private sealed interface TrackingEvent {
        data class LocationFix(val location: Location) : TrackingEvent
        data object Stop : TrackingEvent
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val repository by lazy { (application as KilometrinaApplication).tripRepository }
    private val events = Channel<TrackingEvent>(capacity = Channel.UNLIMITED)

    private var activeTripId: Long? = null
    private var lastLocation: Location? = null
    private var distanceMeters: Double = 0.0
    private var startedAt: Long = 0L
    private var startJob: Job? = null
    private var consumerJob: Job? = null
    private var healthJob: Job? = null
    private var stopping = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                events.trySend(TrackingEvent.LocationFix(location))
            }
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            TrackingDiagnostics.locationAvailabilityChanged(availability.isLocationAvailable)
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        TrackingDiagnostics.trackingStarted()
        createNotificationChannel()
        consumerJob = scope.launch {
            for (event in events) {
                when (event) {
                    is TrackingEvent.LocationFix -> processLocation(event.location)
                    TrackingEvent.Stop -> {
                        finishTracking()
                        break
                    }
                }
            }
        }
        healthJob = scope.launch {
            while (true) {
                delay(5_000L)
                if (activeTripId != null && !stopping) updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTrackingAndFinish()
            else -> startOrResumeTracking()
        }
        return START_STICKY
    }

    private fun startOrResumeTracking() {
        if (startJob?.isActive == true || activeTripId != null || stopping) return
        startJob = scope.launch {
            val trip = repository.getActiveTrip() ?: run {
                stopSelf()
                return@launch
            }
            activeTripId = trip.id
            startedAt = trip.startTime
            distanceMeters = trip.distanceMeters

            ServiceCompat.startForeground(
                this@LocationTrackingService,
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            requestLocationUpdates()
        }
    }

    private fun requestLocationUpdates() {
        val fineGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            stopTrackingAndFinish()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .setMinUpdateDistanceMeters(3f)
            .build()
        fused.requestLocationUpdates(request, locationCallback, mainLooper)
            .addOnFailureListener {
                TrackingDiagnostics.locationAvailabilityChanged(false)
                updateNotification()
            }
    }

    private suspend fun processLocation(location: Location) {
        val tripId = activeTripId ?: return
        val fixElapsedMs = if (location.elapsedRealtimeNanos > 0L) {
            location.elapsedRealtimeNanos / 1_000_000L
        } else {
            SystemClock.elapsedRealtime()
        }
        val fixAgeSeconds = ((SystemClock.elapsedRealtime() - fixElapsedMs).coerceAtLeast(0L)) / 1000.0
        TrackingDiagnostics.locationFix(fixElapsedMs, location.accuracy)

        // Keep the freshest plausible fix for the trip endpoint even when a
        // tiny movement is rejected as jitter.
        if (fixAgeSeconds <= 30.0 && location.accuracy <= 50f) {
            lastLocation = location
        }

        val result = repository.appendLocation(tripId, location)
        result.evaluation?.let { evaluation ->
            if (evaluation.accepted) {
                TrackingDiagnostics.acceptedSegment()
            } else {
                TrackingDiagnostics.rejectedSegment(
                    requireNotNull(evaluation.rejectionReason),
                )
            }
        }
        if (result.addedMeters > 0.0) distanceMeters += result.addedMeters
        updateNotification()
    }

    private fun stopTrackingAndFinish() {
        if (stopping) return
        stopping = true
        startJob?.cancel()
        fused.removeLocationUpdates(locationCallback)
        events.trySend(TrackingEvent.Stop)
    }

    private suspend fun finishTracking() {
        repository.finishTrip(lastLocation)
        activeTripId = null
        TrackingDiagnostics.trackingStopped()
        ServiceCompat.stopForeground(this@LocationTrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sledenje vožnji",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Prikazuje aktivno beleženje kilometrine"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_location)
        .setContentTitle("Kilometrina se beleži")
        .setContentText(notificationText())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                10,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            "Končaj",
            PendingIntent.getService(
                this,
                11,
                Intent(this, LocationTrackingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun notificationText(): String {
        val km = distanceMeters / 1000.0
        val diagnostics = TrackingDiagnostics.state.value
        val ageSeconds = diagnostics.lastFixElapsedRealtimeMs?.let {
            ((SystemClock.elapsedRealtime() - it).coerceAtLeast(0L)) / 1000.0
        }
        val quality = GpsSignalEvaluator.quality(
            isTracking = diagnostics.isTracking,
            locationAvailable = diagnostics.locationAvailable,
            lastFixAgeSeconds = ageSeconds,
            accuracyMeters = diagnostics.lastAccuracyMeters,
        )
        val gpsText = when (quality) {
            GpsSignalQuality.Good -> "GPS ±${diagnostics.lastAccuracyMeters?.toInt() ?: 0} m"
            GpsSignalQuality.Fair -> "GPS srednji"
            GpsSignalQuality.Poor -> "GPS šibek"
            GpsSignalQuality.Lost -> "GPS izgubljen"
            GpsSignalQuality.Unavailable -> "lokacija ni na voljo"
            GpsSignalQuality.Waiting -> "čakam GPS"
        }
        return String.format(Locale.getDefault(), "%.1f km • %s", km, gpsText)
    }

    override fun onDestroy() {
        isRunning = false
        TrackingDiagnostics.trackingStopped()
        fused.removeLocationUpdates(locationCallback)
        events.close()
        healthJob?.cancel()
        consumerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "si.lukabencina.kilometrina.action.START"
        const val ACTION_STOP = "si.lukabencina.kilometrina.action.STOP"
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: android.content.Context) {
            val intent = Intent(context, LocationTrackingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, LocationTrackingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
