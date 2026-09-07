package si.lukabencina.kilometrina

import android.app.Application
import android.os.StrictMode
import si.lukabencina.kilometrina.data.AppDatabase
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
    val tripRepository by lazy {
        TripRepository(
            context = this,
            dao = database.tripDao(),
            savedPlaceRepository = savedPlaceRepository,
        )
    }
    val backupRepository by lazy {
        BackupRepository(
            database = database,
            settingsRepository = settingsRepository,
            savedPlaceRepository = savedPlaceRepository,
            vehicleRepository = vehicleRepository,
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
