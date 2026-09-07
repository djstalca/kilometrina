package si.lukabencina.kilometrina.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.location.LocationTrackingService

sealed interface StartState {
    data object Idle : StartState
    data object Locating : StartState
    data class Error(val message: String) : StartState
}

data class HomeUiState(
    val activeTrip: TripEntity? = null,
    val trips: List<TripEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    val recentTrip: TripEntity?
        get() = trips.firstOrNull { it.endTime != null }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KilometrinaApplication
    private val repository = app.tripRepository
    private val fused = LocationServices.getFusedLocationProviderClient(application)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.activeTrip,
        repository.trips,
        app.settingsRepository.settings,
    ) { active, trips, settings ->
        HomeUiState(
            activeTrip = active,
            trips = trips,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun startTrip(purpose: String, onStateChanged: (StartState) -> Unit) {
        if (!hasLocationPermission()) {
            onStateChanged(StartState.Error("Dovoli lokacijo za beleženje vožnje."))
            return
        }
        viewModelScope.launch {
            onStateChanged(StartState.Locating)
            runCatching {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setDurationMillis(12_000L)
                    .setMaxUpdateAgeMillis(3_000L)
                    .build()
                val location: Location = fused.getCurrentLocation(request, null).await()
                    ?: error("Trenutne lokacije ni bilo mogoče pridobiti.")
                val settings = uiState.value.settings
                val tripId = repository.startTrip(location, purpose, settings.ratePerKm)
                try {
                    LocationTrackingService.start(getApplication())
                } catch (error: Throwable) {
                    repository.deleteTrip(tripId)
                    throw error
                }
            }.onSuccess {
                onStateChanged(StartState.Idle)
            }.onFailure {
                onStateChanged(StartState.Error(it.message ?: "Začetek vožnje ni uspel."))
            }
        }
    }

    fun stopTrip() {
        LocationTrackingService.stop(getApplication())
    }

    fun saveSettings(ratePerKm: Double, defaultPurpose: String) {
        viewModelScope.launch {
            app.settingsRepository.setRatePerKm(ratePerKm)
            app.settingsRepository.setDefaultPurpose(defaultPurpose)
        }
    }

    fun deleteTrip(id: Long) {
        viewModelScope.launch { repository.deleteTrip(id) }
    }

    private fun hasLocationPermission(): Boolean {
        val context = getApplication<Application>()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
