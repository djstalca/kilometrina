package si.lukabencina.kilometrina

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
import si.lukabencina.kilometrina.data.TripKinds
import si.lukabencina.kilometrina.location.LocationTrackingService
import si.lukabencina.kilometrina.quick.KilometrinaWidgetProvider

class QuickTripActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            runCatching { handleAction(intent?.action) }
                .onFailure {
                    Toast.makeText(this@QuickTripActionActivity, it.message ?: "Dejanje ni uspelo.", Toast.LENGTH_LONG).show()
                    openMain()
                }
            KilometrinaWidgetProvider.requestUpdate(this@QuickTripActionActivity)
            finish()
        }
    }

    private suspend fun handleAction(action: String?) {
        val app = application as KilometrinaApplication
        val active = app.tripRepository.getActiveTrip()
        if (action == ACTION_TOGGLE && active != null) {
            LocationTrackingService.stop(this)
            return
        }
        if (active != null) {
            Toast.makeText(this, "Vožnja se že beleži.", Toast.LENGTH_SHORT).show()
            return
        }

        val kind = if (action == ACTION_START_PRIVATE) TripKinds.PRIVATE else TripKinds.BUSINESS
        require(
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        ) { "Najprej v aplikaciji dovoli natančno lokacijo." }

        val manager = getSystemService(LocationManager::class.java)
        require(manager != null && LocationManagerCompat.isLocationEnabled(manager)) { "Vklopi lokacijo (GPS)." }

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(15_000L)
            .setMaxUpdateAgeMillis(2_000L)
            .build()
        val location = try {
            LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(request, null)
                .await()
        } catch (error: SecurityException) {
            throw IllegalStateException("Najprej v aplikaciji dovoli natančno lokacijo.", error)
        } ?: error("Trenutne lokacije ni bilo mogoče pridobiti.")

        require(location.hasAccuracy() && location.accuracy <= 50f) {
            "GPS signal je preslab (±${location.accuracy.toInt()} m)."
        }

        val settings = app.settingsRepository.settings.first()
        val vehicle = app.vehicleRepository.state.first().defaultVehicle
        val purpose = if (kind == TripKinds.PRIVATE) "Zasebna vožnja" else settings.defaultPurpose
        val id = app.tripRepository.startTrip(
            location = location,
            purpose = purpose,
            description = "",
            ratePerKm = settings.ratePerKm,
            vehicle = vehicle,
            tripKind = kind,
        )
        try {
            LocationTrackingService.start(this)
        } catch (error: Throwable) {
            app.tripRepository.deleteTrip(id)
            throw error
        }
    }

    private fun openMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    companion object {
        const val ACTION_TOGGLE = "si.lukabencina.kilometrina.action.QUICK_TOGGLE"
        const val ACTION_START_BUSINESS = "si.lukabencina.kilometrina.action.START_BUSINESS"
        const val ACTION_START_PRIVATE = "si.lukabencina.kilometrina.action.START_PRIVATE"
    }
}
