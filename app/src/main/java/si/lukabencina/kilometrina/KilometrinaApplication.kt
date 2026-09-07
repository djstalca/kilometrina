package si.lukabencina.kilometrina

import android.app.Application
import si.lukabencina.kilometrina.data.AppDatabase
import si.lukabencina.kilometrina.data.BackupRepository
import si.lukabencina.kilometrina.data.SavedPlaceRepository
import si.lukabencina.kilometrina.data.SettingsRepository
import si.lukabencina.kilometrina.data.TripRepository

class KilometrinaApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val tripRepository by lazy { TripRepository(this, database.tripDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val savedPlaceRepository by lazy { SavedPlaceRepository(this) }
    val backupRepository by lazy { BackupRepository(database, settingsRepository, savedPlaceRepository) }
}
