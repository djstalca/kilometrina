package si.lukabencina.kilometrina.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

class BackupRepository(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val savedPlaceRepository: SavedPlaceRepository,
) {
    private val dao = database.tripDao()

    suspend fun createBackupJson(): String {
        check(dao.getActiveTrip() == null) { "Najprej zaključi aktivno vožnjo." }
        return BackupCodec.encode(snapshot())
    }

    suspend fun restoreBackupJson(raw: String): BackupRestoreSummary {
        check(dao.getActiveTrip() == null) { "Obnovitev med aktivno vožnjo ni dovoljena." }
        val incoming = BackupCodec.decode(raw)
        val previous = snapshot()
        try {
            apply(incoming)
        } catch (error: Throwable) {
            runCatching { apply(previous) }
            throw error
        }
        return BackupRestoreSummary(
            tripCount = incoming.trips.size,
            pointCount = incoming.points.size,
            savedPlaceCount = incoming.savedPlaces.size,
        )
    }

    private suspend fun snapshot(): BackupData = BackupData(
        generatedAt = System.currentTimeMillis(),
        settings = settingsRepository.settings.first(),
        savedPlaces = savedPlaceRepository.places.first(),
        trips = dao.getAllTrips(),
        points = dao.getAllPoints(),
    )

    private suspend fun apply(data: BackupData) {
        database.withTransaction {
            dao.deleteAllPoints()
            dao.deleteAllTrips()
            if (data.trips.isNotEmpty()) dao.insertTrips(data.trips)
            if (data.points.isNotEmpty()) dao.insertPoints(data.points)
        }
        settingsRepository.replaceAll(data.settings)
        savedPlaceRepository.replaceAll(data.savedPlaces)
    }
}

data class BackupRestoreSummary(
    val tripCount: Int,
    val pointCount: Int,
    val savedPlaceCount: Int,
)
