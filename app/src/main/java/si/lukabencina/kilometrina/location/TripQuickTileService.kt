package si.lukabencina.kilometrina.location

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lukabencina.kilometrina.KilometrinaApplication

class TripQuickTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val result = QuickTripController.toggle(this@TripQuickTileService)
            withContext(Dispatchers.Main) {
                result.onSuccess { started ->
                    Toast.makeText(
                        this@TripQuickTileService,
                        if (started) "Vožnja se beleži." else "Vožnja se zaključuje.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }.onFailure {
                    Toast.makeText(
                        this@TripQuickTileService,
                        it.message ?: "Dejanje ni uspelo.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                refresh()
            }
        }
    }

    private fun refresh() {
        val active = (application as? KilometrinaApplication)
            ?.runCatching { tripRepository }
            ?.getOrNull()
        qsTile?.apply {
            state = if (LocationTrackingService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (LocationTrackingService.isRunning) "Končaj vožnjo" else "Začni vožnjo"
            updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
