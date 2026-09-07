package si.lukabencina.kilometrina.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lukabencina.kilometrina.KilometrinaApplication

private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024

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
                    val json = repository.createBackupJson()
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
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
                    val raw = readLimited(uri)
                    repository.restoreBackupJson(raw)
                }
            }.onSuccess { summary ->
                mutableState.value = BackupUiState(
                    message = "Obnovljeno: ${summary.tripCount} voženj, ${summary.savedPlaceCount} lokacij in ${summary.pointCount} GPS točk.",
                )
            }.onFailure {
                mutableState.value = BackupUiState(message = it.message ?: "Obnovitev ni uspela.", isError = true)
            }
        }
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null, isError = false)
    }

    private fun readLimited(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val input = resolver.openInputStream(uri) ?: error("Datoteke ni bilo mogoče odpreti.")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BACKUP_BYTES) error("Varnostna kopija je večja od 64 MB.")
                output.write(buffer, 0, read)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}
