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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
    private var stopping = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                events.trySend(TrackingEvent.LocationFix(location))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
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
    }

    private suspend fun processLocation(location: Location) {
        val tripId = activeTripId ?: return
        lastLocation = location
        val added = repository.appendLocation(tripId, location)
        if (added > 0.0) distanceMeters += added
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
        val elapsedMinutes = ((System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 60_000L)
        return String.format(Locale.getDefault(), "%.1f km • %d min", km, elapsedMinutes)
    }

    override fun onDestroy() {
        isRunning = false
        fused.removeLocationUpdates(locationCallback)
        events.close()
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
