package si.lukabencina.kilometrina.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import si.lukabencina.kilometrina.KilometrinaApplication

class DrivingDetectionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED && intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as KilometrinaApplication
                val enabled = app.settingsRepository.settings.first().autoDetectionEnabled
                if (enabled && DrivingDetectionManager.hasPermission(context)) {
                    runCatching { DrivingDetectionManager.enable(context) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
