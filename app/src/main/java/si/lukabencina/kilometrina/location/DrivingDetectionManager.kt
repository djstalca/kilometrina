package si.lukabencina.kilometrina.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.tasks.await

object DrivingDetectionManager {
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun enable(context: Context) {
        check(hasPermission(context)) { "Dovoljenje za prepoznavanje aktivnosti ni odobreno." }
        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(buildRequest(), transitionPendingIntent(context))
            .await()
    }

    suspend fun disable(context: Context) {
        runCatching {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(transitionPendingIntent(context))
                .await()
        }
        DrivingSuggestionNotifications.cancelAll(context)
    }

    private fun buildRequest(): ActivityTransitionRequest {
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
        )
        return ActivityTransitionRequest(transitions)
    }

    private fun transitionPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            7001,
            Intent(context, DrivingTransitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
