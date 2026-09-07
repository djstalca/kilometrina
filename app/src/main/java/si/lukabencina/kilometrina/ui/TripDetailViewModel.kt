package si.lukabencina.kilometrina.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.data.LocationPointEntity

data class TripRouteUiState(
    val tripId: Long? = null,
    val loading: Boolean = false,
    val points: List<LocationPointEntity> = emptyList(),
)

class TripDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as KilometrinaApplication).tripRepository
    private val _state = MutableStateFlow(TripRouteUiState())
    val state: StateFlow<TripRouteUiState> = _state.asStateFlow()

    fun load(tripId: Long) {
        if (_state.value.tripId == tripId && !_state.value.loading) return
        _state.value = TripRouteUiState(tripId = tripId, loading = true)
        viewModelScope.launch {
            val points = runCatching { repository.getRoutePoints(tripId) }.getOrDefault(emptyList())
            _state.value = TripRouteUiState(
                tripId = tripId,
                loading = false,
                points = points,
            )
        }
    }

    fun clear() {
        _state.value = TripRouteUiState()
    }
}
