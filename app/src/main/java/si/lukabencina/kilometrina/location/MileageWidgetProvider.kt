package si.lukabencina.kilometrina.location

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lukabencina.kilometrina.R

class MileageWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        const val ACTION_TOGGLE = "si.lukabencina.kilometrina.widget.TOGGLE"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, MileageWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val running = LocationTrackingService.isRunning
            val views = RemoteViews(context.packageName, R.layout.widget_mileage).apply {
                setTextViewText(R.id.widget_status, if (running) "Vožnja se beleži" else "Pripravljeno")
                setTextViewText(R.id.widget_button, if (running) "Končaj" else "Začni")
                val intent = Intent(context, MileageWidgetActionReceiver::class.java).setAction(ACTION_TOGGLE)
                setOnClickPendingIntent(
                    R.id.widget_button,
                    PendingIntent.getBroadcast(
                        context,
                        9201,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            manager.updateAppWidget(id, views)
        }
    }
}

class MileageWidgetActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != MileageWidgetProvider.ACTION_TOGGLE) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = QuickTripController.toggle(context)
                withContext(Dispatchers.Main) {
                    result.exceptionOrNull()?.let {
                        Toast.makeText(context, it.message ?: "Dejanje ni uspelo.", Toast.LENGTH_LONG).show()
                    }
                    MileageWidgetProvider.updateAll(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
