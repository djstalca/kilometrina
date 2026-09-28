package si.lukabencina.kilometrina.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM location_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getPointsForTrip(tripId: Long): List<LocationPointEntity>

    @Query("SELECT * FROM location_points WHERE tripId = :tripId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPoint(tripId: Long): LocationPointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: TripAttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<TripAttachmentEntity>)

    @Query("SELECT * FROM trip_attachments WHERE tripId = :tripId ORDER BY addedAt DESC")
    suspend fun getAttachmentsForTrip(tripId: Long): List<TripAttachmentEntity>

    @Query("SELECT * FROM trip_attachments ORDER BY id")
    suspend fun getAllAttachments(): List<TripAttachmentEntity>

    @Query("DELETE FROM trip_attachments WHERE id = :attachmentId")
    suspend fun deleteAttachment(attachmentId: Long)

    @Query("DELETE FROM trip_attachments")
    suspend fun deleteAllAttachments()

    @Query("UPDATE trips SET distanceMeters = distanceMeters + :segmentMeters WHERE id = :tripId")
    suspend fun addDistance(tripId: Long, segmentMeters: Double)

    @Query("DELETE FROM location_points")
    suspend fun deleteAllPoints()

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)
}
