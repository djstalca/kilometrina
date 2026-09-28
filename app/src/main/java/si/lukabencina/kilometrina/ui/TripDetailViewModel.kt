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
import si.lukabencina.kilometrina.data.TripGpsAssessment
import si.lukabencina.kilometrina.data.TripGpsQualityEvaluator

data class TripRouteUiState(
    val tripId: Long? = null,
    val loading: Boolean = false,
    val points: List<LocationPointEntity> = emptyList(),
    val attachments: List<TripAttachmentEntity> = emptyList(),
    val assessment: TripGpsAssessment? = null,
    val message: String? = null,
)

class TripDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KilometrinaApplication
    private val repository = app.tripRepository
    private val attachments = app.attachmentRepository
    private val _state = MutableStateFlow(TripRouteUiState())
    val state: StateFlow<TripRouteUiState> = _state.asStateFlow()

    fun load(tripId: Long) {
        if (_state.value.tripId == tripId && !_state.value.loading) return
        refresh(tripId)
    }

    fun addAttachment(tripId: Long, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            runCatching { attachments.addFromUri(tripId, uri) }
                .onSuccess { loadFresh(tripId, "Priloga je dodana.") }
                .onFailure { loadFresh(tripId, it.message ?: "Dodajanje priloge ni uspelo.") }
        }
    }

    fun deleteAttachment(tripId: Long, attachment: TripAttachmentEntity) {
        viewModelScope.launch {
            runCatching { attachments.delete(attachment) }
            loadFresh(tripId, "Priloga je odstranjena.")
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun clear() {
        _state.value = TripRouteUiState()
    }

    private fun refresh(tripId: Long) {
        _state.value = TripRouteUiState(tripId = tripId, loading = true)
        viewModelScope.launch { loadFresh(tripId, null) }
    }

    private suspend fun loadFresh(tripId: Long, message: String?) {
        val points = runCatching { repository.getRoutePoints(tripId) }.getOrDefault(emptyList())
        val tripAttachments = runCatching { attachments.listForTrip(tripId) }.getOrDefault(emptyList())
        _state.value = TripRouteUiState(
            tripId = tripId,
            loading = false,
            points = points,
            attachments = tripAttachments,
            assessment = TripGpsQualityEvaluator.evaluate(points),
            message = message,
        )
    }
}
