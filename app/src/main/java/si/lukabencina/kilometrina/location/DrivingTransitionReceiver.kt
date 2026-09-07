package si.lukabencina.kilometrina.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.MainActivity
import si.lukabencina.kilometrina.R

class DrivingTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as KilometrinaApplication
                val enabled = app.settingsRepository.settings.first().autoDetectionEnabled
                if (!enabled) return@launch
                val activeTrip = app.tripRepository.getActiveTrip()
                result.transitionEvents
                    .filter { it.activityType == DetectedActivity.IN_VEHICLE }
                    .forEach { event ->
                        when (event.transitionType) {
                            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                                if (activeTrip == null) DrivingSuggestionNotifications.showStart(context)
                            }
                            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                                DrivingSuggestionNotifications.cancelStart(context)
                                if (activeTrip != null) DrivingSuggestionNotifications.showFinish(context)
                            }
                        }
                    }
            } finally {
                pending.finish()
            }
        }
    }
}

class DismissDrivingSuggestionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DrivingSuggestionNotifications.cancelAll(context)
    }
}

object DrivingSuggestionNotifications {
    private const val CHANNEL_ID = "drive_suggestions"
    private const val START_ID = 2101
    private const val FINISH_ID = 2102

    fun showStart(context: Context) {
        if (!canNotify(context)) return
        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            7101,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context,
            7102,
            Intent(context, DismissDrivingSuggestionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle("Kaže, da si začel vožnjo")
            .setContentText("Odpri Kilometrino in z enim dotikom začni beleženje.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Začni", openApp)
            .addAction(0, "Prezri", dismiss)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(START_ID, notification)
    }

    fun showFinish(context: Context) {
        if (!canNotify(context)) return
        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            7103,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            context,
            7104,
            Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle("Je vožnja končana?")
            .setContentText("Zaznan je izstop iz vozila. Preveri ali zaključi beleženje.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Končaj", stop)
            .addAction(0, "Odpri", openApp)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(FINISH_ID, notification)
    }

    fun cancelStart(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(START_ID)
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(START_ID)
        manager.cancel(FINISH_ID)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Predlogi za vožnjo",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Opcijski predlogi za začetek in konec beleženja kilometrine"
            },
        )
    }
}
