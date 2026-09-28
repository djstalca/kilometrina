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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.AttachmentEntity
import si.lukabencina.kilometrina.data.SavedPlace
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.data.Vehicle
import si.lukabencina.kilometrina.data.VehicleState
import si.lukabencina.kilometrina.data.routeStops
import si.lukabencina.kilometrina.location.DrivingDetectionManager
import si.lukabencina.kilometrina.location.DrivingSuggestionNotifications
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
    val vehicleState: VehicleState = VehicleState(),
    val recoveryRequired: Boolean = false,
    val trackingDiagnostics: TrackingDiagnosticsState = TrackingDiagnosticsState(),
    val attachments: List<AttachmentEntity> = emptyList(),
) {
    val recentTrip: TripEntity?
        get() = trips.firstOrNull { it.endTime != null }

    val recentLocations: List<String>
        get() = trips.asSequence()
            .filter { it.endTime != null }
            .flatMap { trip ->
                sequence {
                    yield(trip.startAddress)
                    trip.routeStops().forEach { yield(it) }
                    trip.endAddress?.let { yield(it) }
                }
            }
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

private data class TripData(
    val active: TripEntity?,
    val trips: List<TripEntity>,
    val attachments: List<AttachmentEntity>,
)

private data class PreferencesState(
    val settings: AppSettings,
    val savedPlaces: List<SavedPlace>,
    val vehicles: VehicleState,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KilometrinaApplication
    private val repository = app.tripRepository
    private val fused = LocationServices.getFusedLocationProviderClient(application)
    private val recoveryRequired = MutableStateFlow(false)

    private val preferences = combine(
        app.settingsRepository.settings,
        app.savedPlaceRepository.places,
        app.vehicleRepository.state,
    ) { settings, savedPlaces, vehicles ->
        PreferencesState(settings, savedPlaces, vehicles)
    }

    private val tripData = combine(
        repository.activeTrip,
        repository.trips,
        repository.attachments,
    ) { active, trips, attachments ->
        TripData(active, trips, attachments)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        tripData,
        preferences,
        recoveryRequired,
        TrackingDiagnostics.state,
    ) { tripDataValue, preferencesValue, recovery, diagnostics ->
        HomeUiState(
            activeTrip = tripDataValue.active,
            trips = tripDataValue.trips,
            settings = preferencesValue.settings,
            savedPlaces = preferencesValue.savedPlaces,
            vehicleState = preferencesValue.vehicles,
            recoveryRequired = recovery && tripDataValue.active != null,
            trackingDiagnostics = diagnostics,
            attachments = tripDataValue.attachments,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            recoveryRequired.value = repository.getActiveTrip() != null && !LocationTrackingService.isRunning
            val settings = app.settingsRepository.settings.first()
            val vehicles = app.vehicleRepository.state.first()
            if (vehicles.vehicles.isEmpty()) {
                app.vehicleRepository.seedLegacyIfEmpty(settings.vehicleName, settings.registrationPlate)
            }
            runCatching { app.savedPlaceRepository.resolveMissingCoordinates() }
            if (settings.autoDetectionEnabled && DrivingDetectionManager.hasPermission(application)) {
                runCatching { DrivingDetectionManager.enable(application) }
            }
        }
    }

    fun startTrip(purpose: String, description: String, onStateChanged: (StartState) -> Unit) {
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
                val context = getApplication<Application>()
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    error("Dovoli natančno lokacijo za beleženje vožnje.")
                }
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setDurationMillis(15_000L)
                    .setMaxUpdateAgeMillis(2_000L)
                    .build()
                val location: Location = try {
                    fused.getCurrentLocation(request, null).await()
                } catch (error: SecurityException) {
                    throw IllegalStateException("Dovoli natančno lokacijo za beleženje vožnje.", error)
                } ?: error("Trenutne lokacije ni bilo mogoče pridobiti.")
                if (!location.hasAccuracy() || location.accuracy > 50f) {
                    error("GPS signal je trenutno preslab (±${location.accuracy.toInt()} m). Premakni se na bolj odprto mesto in poskusi znova.")
                }
                val state = uiState.value
                val tripId = repository.startTrip(
                    location = location,
                    purpose = purpose,
                    description = description,
                    ratePerKm = state.settings.ratePerKm,
                    vehicle = state.vehicleState.defaultVehicle,
                )
                try {
                    LocationTrackingService.start(getApplication())
                } catch (error: Throwable) {
                    repository.deleteTrip(tripId)
                    throw error
                }
            }.onSuccess {
                DrivingSuggestionNotifications.cancelAll(getApplication())
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
            DrivingSuggestionNotifications.cancelAll(getApplication())
            recoveryRequired.value = false
        }
    }

    fun stopTrip() {
        DrivingSuggestionNotifications.cancelAll(getApplication())
        LocationTrackingService.stop(getApplication())
    }

    fun saveSettings(
        ratePerKm: Double,
        defaultPurpose: String,
        driverName: String,
        companyName: String,
    ) {
        viewModelScope.launch {
            app.settingsRepository.setRatePerKm(ratePerKm)
            app.settingsRepository.setDefaultPurpose(defaultPurpose)
            app.settingsRepository.setIdentityProfile(driverName, companyName)
        }
    }

    fun setCalendarIntegrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.settingsRepository.setCalendarIntegrationEnabled(enabled && app.calendarSuggestionRepository.hasPermission())
        }
    }

    fun setAutoDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val registered = runCatching { DrivingDetectionManager.enable(getApplication()) }.isSuccess
                app.settingsRepository.setAutoDetectionEnabled(registered)
            } else {
                app.settingsRepository.setAutoDetectionEnabled(false)
                DrivingDetectionManager.disable(getApplication())
            }
        }
    }

    fun saveVehicle(vehicle: Vehicle, makeDefault: Boolean) {
        viewModelScope.launch { app.vehicleRepository.upsert(vehicle, makeDefault) }
    }

    fun deleteVehicle(id: String) {
        viewModelScope.launch { app.vehicleRepository.delete(id) }
    }

    fun setDefaultVehicle(id: String) {
        viewModelScope.launch { app.vehicleRepository.setDefault(id) }
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

    fun addAttachment(tripId: Long, displayName: String, mimeType: String, data: ByteArray) {
        viewModelScope.launch { repository.addAttachment(tripId, displayName, mimeType, data) }
    }

    fun deleteAttachment(id: Long) {
        viewModelScope.launch { repository.deleteAttachment(id) }
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
