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

    @Query("SELECT * FROM location_points WHERE tripId = :tripId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPoint(tripId: Long): LocationPointEntity?

    @Query("UPDATE trips SET distanceMeters = distanceMeters + :segmentMeters WHERE id = :tripId")
    suspend fun addDistance(tripId: Long, segmentMeters: Double)

    @Query("DELETE FROM location_points")
    suspend fun deleteAllPoints()

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)
}
