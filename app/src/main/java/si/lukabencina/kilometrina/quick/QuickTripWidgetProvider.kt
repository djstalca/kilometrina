package si.lukabencina.kilometrina.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.R

class QuickTripWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, QuickTripWidgetProvider::class.java)
            updateWidgets(context, manager, manager.getAppWidgetIds(component))
        }

        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val pending = context.applicationContext
            CoroutineScope(Dispatchers.Main).launch {
                val app = pending as KilometrinaApplication
                val active = app.tripRepository.getActiveTrip() != null
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_quick_trip)
                    views.setTextViewText(R.id.widget_title, if (active) "Kilometrina se beleži" else "Kilometrina")
                    views.setTextViewText(R.id.widget_toggle, if (active) "Končaj" else "Začni vožnjo")
                    val intent = Intent(context, QuickTripActivity::class.java)
                    val action = PendingIntent.getActivity(
                        context,
                        300 + id,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    views.setOnClickPendingIntent(R.id.widget_toggle, action)
                    manager.updateAppWidget(id, views)
                }
            }
        }
    }
}
