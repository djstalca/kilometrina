package si.lukabencina.kilometrina.quick

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import si.lukabencina.kilometrina.QuickTripActionActivity
import si.lukabencina.kilometrina.location.LocationTrackingService

class KilometrinaTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickTripActionActivity::class.java)
            .setAction(QuickTripActionActivity.ACTION_TOGGLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                8301,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile() {
        val running = LocationTrackingService.isRunning
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (running) "Končaj vožnjo" else "Začni kilometrino"
            subtitle = if (running) "GPS sledenje aktivno" else "Hiter začetek"
            updateTile()
        }
    }
}
