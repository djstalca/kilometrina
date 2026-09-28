package si.lukabencina.kilometrina.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import si.lukabencina.kilometrina.QuickTripActionActivity
import si.lukabencina.kilometrina.R
import si.lukabencina.kilometrina.location.LocationTrackingService

class KilometrinaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, KilometrinaWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val running = LocationTrackingService.isRunning
            val intent = Intent(context, QuickTripActionActivity::class.java)
                .setAction(QuickTripActionActivity.ACTION_TOGGLE)
            val pending = PendingIntent.getActivity(
                context,
                8401,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_kilometrina)
            views.setTextViewText(R.id.widget_status, if (running) "Vožnja se beleži" else "Pripravljeno")
            views.setTextViewText(R.id.widget_action, if (running) "Končaj" else "Začni")
            views.setOnClickPendingIntent(R.id.widget_action, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
