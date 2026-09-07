package si.lukabencina.kilometrina.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.SavedPlace
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.location.LocationTrackingService
import si.lukabencina.kilometrina.location.TrackingDiagnostics
import si.lukabencina.kilometrina.location.TrackingDiagnosticsState

sealed interface StartState {
    data object Idle : StartState
    data object Locating : StartState
    data class Error(val message: String) : StartState
}

data class HomeUiState(
    val activeTrip: TripEntity? = null,
    val trips: List<TripEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val savedPlaces: List<SavedPlace> = emptyList(),
    val recoveryRequired: Boolean = false,
    val trackingDiagnostics: TrackingDiagnosticsState = TrackingDiagnosticsState(),
) {
    val recentTrip: TripEntity?
        get() = trips.firstOrNull { it.endTime != null }

    val recentLocations: List<String>
        get() = trips.asSequence()
            .filter { it.endTime != null }
            .flatMap { sequenceOf(it.startAddress, it.endAddress) }
            .filterNotNull()
            .map(String::trim)
            .filter { it.isNotBlank() && it != "Lokacija ni na voljo" }
            .distinct()
            .take(6)
            .toList()

    val recentPurposes: List<String>
        get() = trips.asSequence()
            .filter { it.endTime != null }
            .map { it.purpose.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
            .toList()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KilometrinaApplication
    private val repository = app.tripRepository
    private val fused = LocationServices.getFusedLocationProviderClient(application)
    private val recoveryRequired = MutableStateFlow(false)

    private val preferences = combine(
        app.settingsRepository.settings,
        app.savedPlaceRepository.places,
    ) { settings, savedPlaces -> settings to savedPlaces }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.activeTrip,
        repository.trips,
        preferences,
        recoveryRequired,
        TrackingDiagnostics.state,
    ) { active, trips, preferencesValue, recovery, diagnostics ->
        HomeUiState(
            activeTrip = active,
            trips = trips,
            settings = preferencesValue.first,
            savedPlaces = preferencesValue.second,
            recoveryRequired = recovery && active != null,
            trackingDiagnostics = diagnostics,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            recoveryRequired.value = repository.getActiveTrip() != null && !LocationTrackingService.isRunning
        }
    }

    fun startTrip(purpose: String, onStateChanged: (StartState) -> Unit) {
        if (!hasLocationPermission()) {
            onStateChanged(StartState.Error("Dovoli natančno lokacijo za beleženje vožnje."))
            return
        }
        if (!isLocationEnabled()) {
            onStateChanged(StartState.Error("Vklopi lokacijo (GPS) na telefonu in poskusi znova."))
            return
        }
        viewModelScope.launch {
            onStateChanged(StartState.Locating)
            runCatching {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setDurationMillis(15_000L)
                    .setMaxUpdateAgeMillis(2_000L)
                    .build()
                val location: Location = fused.getCurrentLocation(request, null).await()
                    ?: error("Trenutne lokacije ni bilo mogoče pridobiti.")
                if (!location.hasAccuracy() || location.accuracy > 50f) {
                    error("GPS signal je trenutno preslab (±${location.accuracy.toInt()} m). Premakni se na bolj odprto mesto in poskusi znova.")
                }
                val settings = uiState.value.settings
                val tripId = repository.startTrip(location, purpose, settings.ratePerKm)
                try {
                    LocationTrackingService.start(getApplication())
                } catch (error: Throwable) {
                    repository.deleteTrip(tripId)
                    throw error
                }
            }.onSuccess {
                recoveryRequired.value = false
                onStateChanged(StartState.Idle)
            }.onFailure {
                onStateChanged(StartState.Error(it.message ?: "Začetek vožnje ni uspel."))
            }
        }
    }

    fun resumeRecoveredTrip(onStateChanged: (StartState) -> Unit) {
        if (!hasLocationPermission()) {
            onStateChanged(StartState.Error("Dovoli natančno lokacijo, da lahko nadaljujem sledenje."))
            return
        }
        if (!isLocationEnabled()) {
            onStateChanged(StartState.Error("Vklopi lokacijo (GPS), preden nadaljuješ sledenje."))
            return
        }
        runCatching {
            LocationTrackingService.start(getApplication())
        }.onSuccess {
            recoveryRequired.value = false
            onStateChanged(StartState.Idle)
        }.onFailure {
            onStateChanged(StartState.Error(it.message ?: "Nadaljevanje sledenja ni uspelo."))
        }
    }

    fun finishRecoveredTrip() {
        viewModelScope.launch {
            repository.finishTrip(null)
            recoveryRequired.value = false
        }
    }

    fun stopTrip() {
        LocationTrackingService.stop(getApplication())
    }

    fun saveSettings(
        ratePerKm: Double,
        defaultPurpose: String,
        driverName: String,
        companyName: String,
        vehicleName: String,
        registrationPlate: String,
    ) {
        viewModelScope.launch {
            app.settingsRepository.setRatePerKm(ratePerKm)
            app.settingsRepository.setDefaultPurpose(defaultPurpose)
            app.settingsRepository.setReportProfile(
                driverName = driverName,
                companyName = companyName,
                vehicleName = vehicleName,
                registrationPlate = registrationPlate,
            )
        }
    }

    fun savePlace(place: SavedPlace) {
        viewModelScope.launch { app.savedPlaceRepository.upsert(place) }
    }

    fun deletePlace(id: String) {
        viewModelScope.launch { app.savedPlaceRepository.delete(id) }
    }

    fun addManualTrip(trip: TripEntity) {
        viewModelScope.launch { repository.addManualTrip(trip) }
    }

    fun updateTrip(trip: TripEntity) {
        viewModelScope.launch { repository.updateCompletedTrip(trip) }
    }

    fun deleteTrip(id: Long) {
        viewModelScope.launch { repository.deleteTrip(id) }
    }

    private fun hasLocationPermission(): Boolean {
        val context = getApplication<Application>()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val manager = getApplication<Application>().getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }
}
