package si.lukabencina.kilometrina.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.TripEntity

data class MonthlyPackageUiState(
    val working: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class MonthlyPackageViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KilometrinaApplication
    private val mutableState = MutableStateFlow(MonthlyPackageUiState())
    val state: StateFlow<MonthlyPackageUiState> = mutableState.asStateFlow()

    fun export(uri: Uri, month: YearMonth, trips: List<TripEntity>, settings: AppSettings) {
        if (mutableState.value.working) return
        viewModelScope.launch {
            mutableState.value = MonthlyPackageUiState(working = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val business = trips.filter { it.endTime != null && it.tripType != "PRIVATE" }
                    val attachments = business.flatMap { app.tripRepository.getAttachments(it.id) }
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri, "w")?.use { output ->
                        MonthlyPackageExporter.write(output, business, month, settings, attachments)
                    } ?: error("Datoteke ni bilo mogoče ustvariti.")
                }
            }.onSuccess {
                mutableState.value = MonthlyPackageUiState(message = "Mesečni paket je pripravljen.")
            }.onFailure {
                mutableState.value = MonthlyPackageUiState(
                    message = it.message ?: "Priprava paketa ni uspela.",
                    isError = true,
                )
            }
        }
    }

    fun clearMessage() {
        mutableState.value = MonthlyPackageUiState()
    }
}
