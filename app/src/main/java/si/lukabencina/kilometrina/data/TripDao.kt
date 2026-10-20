package si.lukabencina.kilometrina.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayOutputStream
import java.io.OutputStream

@Dao
interface TripDao {
    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripEntity>)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: LocationPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<LocationPointEntity>)

    @Insert
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM trips WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeActiveTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY id")
    suspend fun getAllTrips(): List<TripEntity>

    @Query("SELECT * FROM location_points ORDER BY id")
    suspend fun getAllPoints(): List<LocationPointEntity>

    @Query("SELECT id, tripId, displayName, mimeType, createdAt, length(data) AS sizeBytes FROM attachments ORDER BY createdAt, id")
    suspend fun getAttachmentSummaries(): List<AttachmentSummary>

    @Query("SELECT id, tripId, displayName, mimeType, createdAt, length(data) AS sizeBytes FROM attachments ORDER BY createdAt DESC, id DESC")
    fun observeAttachments(): Flow<List<AttachmentSummary>>

    @Query("SELECT id, tripId, displayName, mimeType, createdAt, length(data) AS sizeBytes FROM attachments WHERE tripId = :tripId ORDER BY createdAt, id")
    suspend fun getAttachmentsForTrip(tripId: Long): List<AttachmentSummary>

    // A single camera photo can exceed SQLite's CursorWindow. Keep the existing
    // BLOB intact and read bounded slices; this also recovers already affected DBs.
    @Query("SELECT substr(data, :offset, :length) FROM attachments WHERE id = :attachmentId")
    suspend fun getAttachmentChunk(attachmentId: Long, offset: Long, length: Int): ByteArray?

    @Transaction
    suspend fun copyAttachment(attachment: AttachmentSummary, output: OutputStream) {
        var offset = 0L
        while (offset < attachment.sizeBytes) {
            val chunk = getAttachmentChunk(attachment.id, offset + 1, minOf(256 * 1024L, attachment.sizeBytes - offset).toInt())
                ?: error("Priloge ni bilo mogoče prebrati.")
            check(chunk.isNotEmpty()) { "Priloga ni popolna." }
            output.write(chunk)
            offset += chunk.size
        }
    }

    @Transaction
    suspend fun getAllAttachments(): List<AttachmentEntity> = getAttachmentSummaries().map { summary ->
        val bytes = ByteArrayOutputStream().use { output ->
            copyAttachment(summary, output)
            output.toByteArray()
        }
        AttachmentEntity(summary.id, summary.tripId, summary.displayName, summary.mimeType, bytes, summary.createdAt)
    }

    @Query("SELECT * FROM location_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getPointsForTrip(tripId: Long): List<LocationPointEntity>

    @Query("SELECT * FROM location_points WHERE tripId = :tripId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPoint(tripId: Long): LocationPointEntity?

    @Query("UPDATE trips SET distanceMeters = distanceMeters + :segmentMeters WHERE id = :tripId")
    suspend fun addDistance(tripId: Long, segmentMeters: Double)

    @Transaction
    suspend fun appendPointAndDistance(point: LocationPointEntity) {
        insertPoint(point)
        addDistance(point.tripId, point.segmentMeters)
    }

    @Query("UPDATE trips SET routeReviewPending = 0 WHERE id = :tripId")
    suspend fun confirmRouteReview(tripId: Long)

    @Query("DELETE FROM attachments WHERE id = :attachmentId")
    suspend fun deleteAttachment(attachmentId: Long)

    @Query("DELETE FROM attachments")
    suspend fun deleteAllAttachments()

    @Query("DELETE FROM location_points")
    suspend fun deleteAllPoints()

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)
}
