package si.lukabencina.kilometrina.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import si.lukabencina.kilometrina.KilometrinaApplication

object QuickTripController {
    suspend fun toggle(context: Context): Result<Boolean> = runCatching {
        val app = context.applicationContext as KilometrinaApplication
        val active = app.tripRepository.getActiveTrip()
        if (active != null) {
            LocationTrackingService.stop(context)
            false
        } else {
            require(
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED,
            ) { "Najprej v aplikaciji dovoli natančno lokacijo." }
            val locationManager = context.getSystemService(LocationManager::class.java)
                ?: error("Lokacijska storitev ni na voljo.")
            require(LocationManagerCompat.isLocationEnabled(locationManager)) { "Vklopi GPS in poskusi znova." }

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(15_000L)
                .setMaxUpdateAgeMillis(2_000L)
                .build()
            val location = try {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(request, null)
                    .await()
            } catch (error: SecurityException) {
                throw IllegalStateException("Najprej v aplikaciji dovoli natančno lokacijo.", error)
            } ?: error("Trenutne lokacije ni bilo mogoče pridobiti.")
            require(location.hasAccuracy() && location.accuracy <= 50f) {
                "GPS signal je trenutno preslab."
            }

            val settings = app.settingsRepository.settings.first()
            val vehicle = app.vehicleRepository.state.first().defaultVehicle
            app.tripRepository.startTrip(
                location = location,
                purpose = settings.defaultPurpose,
                ratePerKm = settings.ratePerKm,
                vehicle = vehicle,
            )
            try {
                LocationTrackingService.start(context)
            } catch (error: Throwable) {
                app.tripRepository.getActiveTrip()?.let { app.tripRepository.deleteTrip(it.id) }
                throw error
            }
            true
        }
    }
}
