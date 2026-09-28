package si.lukabencina.kilometrina.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lukabencina.kilometrina.KilometrinaApplication
import si.lukabencina.kilometrina.location.DrivingDetectionManager

data class BackupUiState(
    val working: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KilometrinaApplication
    private val repository = app.backupRepository
    private val mutableState = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = mutableState.asStateFlow()

    fun exportTo(uri: Uri) {
        if (mutableState.value.working) return
        viewModelScope.launch {
            mutableState.value = BackupUiState(working = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri, "w")?.use { repository.writeBackup(it) }
                        ?: error("Datoteke ni bilo mogoče odpreti za zapis.")
                }
            }.onSuccess {
                mutableState.value = BackupUiState(message = "Varnostna kopija je shranjena.")
            }.onFailure {
                mutableState.value = BackupUiState(message = it.message ?: "Izvoz ni uspel.", isError = true)
            }
        }
    }

    fun restoreFrom(uri: Uri) {
        if (mutableState.value.working) return
        viewModelScope.launch {
            mutableState.value = BackupUiState(working = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val input = resolver.openInputStream(uri) ?: error("Datoteke ni bilo mogoče odpreti.")
                    val summary = input.use { repository.restoreBackup(it) }
                    syncDriveDetectionAfterRestore()
                    summary
                }
            }.onSuccess { summary ->
                mutableState.value = BackupUiState(
                    message = "Obnovljeno: ${summary.tripCount} voženj, ${summary.savedPlaceCount} lokacij, ${summary.vehicleCount} vozil, ${summary.pointCount} GPS točk in ${summary.attachmentCount} prilog.",
                )
            }.onFailure {
                mutableState.value = BackupUiState(message = it.message ?: "Obnovitev ni uspela.", isError = true)
            }
        }
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null, isError = false)
    }

    private suspend fun syncDriveDetectionAfterRestore() {
        val enabled = app.settingsRepository.settings.first().autoDetectionEnabled
        if (!enabled) {
            DrivingDetectionManager.disable(getApplication())
            return
        }
        if (!DrivingDetectionManager.hasPermission(getApplication())) {
            app.settingsRepository.setAutoDetectionEnabled(false)
            return
        }
        val registered = runCatching { DrivingDetectionManager.enable(getApplication()) }.isSuccess
        if (!registered) app.settingsRepository.setAutoDetectionEnabled(false)
    }


}
