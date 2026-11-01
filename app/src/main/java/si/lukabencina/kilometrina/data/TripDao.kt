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
    @Transaction
    suspend fun getExportSnapshot(): ReportExportSnapshot =
        ReportExportSnapshot(getAllTrips(), getAttachmentSummaries(), getExpenses())
    @Query("SELECT * FROM expenses ORDER BY createdAt, id")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY createdAt, id")
    suspend fun getExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY createdAt, id")
    suspend fun getTripExpenses(tripId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpense(id: String): ExpenseEntity?

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: Long): TripEntity?

    @Insert
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun removeExpense(id: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("UPDATE trips SET parkingCents = :parking, tollsCents = :tolls WHERE id = :tripId")
    suspend fun setExpenseTotals(tripId: Long, parking: Int, tolls: Int)

    @Transaction
    suspend fun recalculateExpenses(tripId: Long) {
        val expenses = getTripExpenses(tripId)
        fun total(kind: String): Int = expenses.filter { it.kind == kind }.sumOf { it.amountCents.toLong() }.also {
            require(it <= Int.MAX_VALUE) { "Skupni stroški so previsoki." }
        }.toInt()
        setExpenseTotals(tripId, total(ExpenseKinds.PARKING), total(ExpenseKinds.TOLL))
    }

    @Transaction
    suspend fun seedExpenses(tripId: Long) {
        val trip = requireNotNull(getTrip(tripId)) { "Vožnja ne obstaja več." }
        val present = getTripExpenses(tripId).map { it.kind }.toSet()
        val missing = ExpenseRules.legacy(listOf(trip)).filter { it.kind !in present }
        if (missing.isNotEmpty()) insertExpenses(missing)
    }

    @Transaction
    suspend fun saveExpense(expense: ExpenseEntity) {
        ExpenseRules.validate(expense)
        seedExpenses(expense.tripId)
        val old = getExpense(expense.id)
        require(old == null || old.tripId == expense.tripId) { "Strošek pripada drugi vožnji." }
        val safe = expense.copy(attachmentId = old?.attachmentId, createdAt = old?.createdAt ?: expense.createdAt)
        if (old == null) insertExpenses(listOf(safe)) else updateExpense(safe)
        recalculateExpenses(expense.tripId)
    }

    @Transaction
    suspend fun deleteExpense(id: String) {
        val expense = getExpense(id) ?: return
        removeExpense(id)
        recalculateExpenses(expense.tripId)
        // Receipts are retained with the trip; removing a cost never destroys a file.
    }

    @Transaction
    suspend fun attachExpenseReceipt(id: String, attachment: AttachmentEntity) {
        val expense = requireNotNull(getExpense(id)) { "Strošek ne obstaja več." }
        require(expense.tripId == attachment.tripId) { "Račun pripada drugi vožnji." }
        updateExpense(expense.copy(attachmentId = insertAttachment(attachment)))
    }

    @Transaction
    suspend fun updateCompletedDetails(trip: TripEntity) {
        val latest = requireNotNull(getTrip(trip.id)) { "Vožnja ne obstaja več." }
        require(latest.endTime != null)
        updateTrip(trip.copy(parkingCents = latest.parkingCents, tollsCents = latest.tollsCents))
    }

    @Transaction
    suspend fun completeTrip(completed: TripEntity) {
        val latest = getTrip(completed.id) ?: return
        if (latest.endTime != null) return
        updateTrip(completed.copy(distanceMeters = latest.distanceMeters,
            parkingCents = latest.parkingCents, tollsCents = latest.tollsCents))
    }

    @Transaction
    suspend fun insertManualWithExpenses(trip: TripEntity): Long {
        val id = insertTrip(trip)
        seedExpenses(id)
        return id
    }
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
