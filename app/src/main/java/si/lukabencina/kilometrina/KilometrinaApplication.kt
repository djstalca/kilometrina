package si.lukabencina.kilometrina

import android.app.Application
import android.os.StrictMode
import si.lukabencina.kilometrina.data.AppDatabase
import si.lukabencina.kilometrina.data.AttachmentRepository
import si.lukabencina.kilometrina.data.BackupRepository
import si.lukabencina.kilometrina.data.SavedPlaceRepository
import si.lukabencina.kilometrina.data.SettingsRepository
import si.lukabencina.kilometrina.data.TripRepository
import si.lukabencina.kilometrina.data.VehicleRepository
import si.lukabencina.kilometrina.diagnostics.LocalCrashReporter

class KilometrinaApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val savedPlaceRepository by lazy { SavedPlaceRepository(this) }
    val vehicleRepository by lazy { VehicleRepository(this) }
    val attachmentRepository by lazy { AttachmentRepository(this, database.tripDao()) }
    val tripRepository by lazy {
        TripRepository(
            context = this,
            dao = database.tripDao(),
            savedPlaceRepository = savedPlaceRepository,
            attachmentRepository = attachmentRepository,
        )
    }
    val backupRepository by lazy {
        BackupRepository(
            database = database,
            settingsRepository = settingsRepository,
            savedPlaceRepository = savedPlaceRepository,
            vehicleRepository = vehicleRepository,
            attachmentRepository = attachmentRepository,
        )
    }

    override fun onCreate() {
        super.onCreate()
        LocalCrashReporter.install(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build(),
            )
        }
    }
}
