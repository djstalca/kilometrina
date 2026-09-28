package si.lukabencina.kilometrina.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.data.LocationPointEntity
import si.lukabencina.kilometrina.data.TripAttachmentEntity

data class TripRouteUiState(
    val tripId: Long? = null,
    val loading: Boolean = false,
    val points: List<LocationPointEntity> = emptyList(),
    val attachments: List<TripAttachmentEntity> = emptyList(),
    val message: String? = null,
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
            val attachments = runCatching { repository.getAttachments(tripId) }.getOrDefault(emptyList())
            _state.value = TripRouteUiState(
                tripId = tripId,
                loading = false,
                points = points,
                attachments = attachments,
            )
        }
    }

    fun addAttachment(tripId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.addAttachment(tripId, uri) }
                .onSuccess { loadFresh(tripId, "Priloga je dodana.") }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Dodajanje priloge ni uspelo.") }
        }
    }

    fun deleteAttachment(tripId: Long, attachmentId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteAttachment(attachmentId) }
                .onSuccess { loadFresh(tripId, "Priloga je odstranjena.") }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Brisanje priloge ni uspelo.") }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private suspend fun loadFresh(tripId: Long, message: String? = null) {
        val points = repository.getRoutePoints(tripId)
        val attachments = repository.getAttachments(tripId)
        _state.value = TripRouteUiState(
            tripId = tripId,
            loading = false,
            points = points,
            attachments = attachments,
            message = message,
        )
    }

    fun clear() {
        _state.value = TripRouteUiState()
    }
}
