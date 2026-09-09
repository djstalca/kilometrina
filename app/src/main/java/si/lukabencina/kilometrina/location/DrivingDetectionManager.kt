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
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            error("Dovoljenje za prepoznavanje aktivnosti ni odobreno.")
        }
        try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(buildRequest(), transitionPendingIntent(context))
                .await()
        } catch (error: SecurityException) {
            throw IllegalStateException("Dovoljenje za prepoznavanje aktivnosti ni odobreno.", error)
        }
    }

    suspend fun disable(context: Context) {
        val canAccess = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
        if (canAccess) {
            try {
                ActivityRecognition.getClient(context)
                    .removeActivityTransitionUpdates(transitionPendingIntent(context))
                    .await()
            } catch (_: SecurityException) {
                // Permission can be revoked between the check and the API call.
            } catch (_: Throwable) {
                // Best-effort cleanup; disabling should still clear local notifications/state.
            }
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
