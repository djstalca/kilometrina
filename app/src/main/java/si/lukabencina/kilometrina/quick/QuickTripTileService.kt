package si.lukabencina.kilometrina.quick

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import si.lukabencina.kilometrina.KilometrinaApplication

class QuickTripTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        CoroutineScope(Dispatchers.Main).launch {
            val active = (application as KilometrinaApplication).tripRepository.getActiveTrip() != null
            qsTile?.apply {
                state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                label = if (active) "Končaj vožnjo" else "Začni vožnjo"
                updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickTripActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                200,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun requestRefresh(context: Context) {
            requestListeningState(context, ComponentName(context, QuickTripTileService::class.java))
        }
    }
}
