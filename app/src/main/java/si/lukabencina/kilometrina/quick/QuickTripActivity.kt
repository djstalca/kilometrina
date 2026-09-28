package si.lukabencina.kilometrina.quick

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.MainActivity
import si.lukabencina.kilometrina.location.DrivingSuggestionNotifications
import si.lukabencina.kilometrina.location.LocationTrackingService

class QuickTripActivity : ComponentActivity() {
    private val app by lazy { application as KilometrinaApplication }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { toggleTrip() }
    }

    private suspend fun toggleTrip() {
        val active = app.tripRepository.getActiveTrip()
        if (active != null) {
            DrivingSuggestionNotifications.cancelAll(this)
            LocationTrackingService.stop(this)
            Toast.makeText(this, "Vožnja se zaključuje.", Toast.LENGTH_SHORT).show()
            QuickTripWidgetProvider.updateAll(this)
            QuickTripTileService.requestRefresh(this)
            finish()
            return
        }

        if (!hasFineLocation() || !isLocationEnabled()) {
            Toast.makeText(this, "Za hiter začetek najprej dovoli GPS v aplikaciji.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        runCatching {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(15_000L)
                .setMaxUpdateAgeMillis(2_000L)
                .build()
            val location = LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(request, null)
                .await()
                ?: error("Trenutne lokacije ni bilo mogoče pridobiti.")
            require(location.hasAccuracy() && location.accuracy <= 50f) {
                "GPS signal je preslab za varen začetek vožnje."
            }

            val settings = app.settingsRepository.settings.first()
            val vehicles = app.vehicleRepository.state.first()
            val tripId = app.tripRepository.startTrip(
                location = location,
                purpose = settings.defaultPurpose,
                ratePerKm = settings.ratePerKm,
                vehicle = vehicles.defaultVehicle,
            )
            try {
                LocationTrackingService.start(this)
            } catch (error: Throwable) {
                app.tripRepository.deleteTrip(tripId)
                throw error
            }
        }.onSuccess {
            DrivingSuggestionNotifications.cancelAll(this)
            Toast.makeText(this, "Beleženje vožnje se je začelo.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, it.message ?: "Začetek vožnje ni uspel.", Toast.LENGTH_LONG).show()
        }

        QuickTripWidgetProvider.updateAll(this)
        QuickTripTileService.requestRefresh(this)
        finish()
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }
}
