package si.lukabencina.kilometrina.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object TripKinds {
    const val BUSINESS = "BUSINESS"
    const val PRIVATE = "PRIVATE"
}

object GpsQuality {
    const val UNKNOWN = "UNKNOWN"
    const val GOOD = "GOOD"
    const val FAIR = "FAIR"
    const val POOR = "POOR"
}

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val startLat: Double,
    val startLon: Double,
    val startAddress: String,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val endAddress: String? = null,
    val distanceMeters: Double = 0.0,
    val purpose: String = "Službena pot",
    val description: String = "",
    val routeStopsJson: String = "[]",
    val routeDetectionVersion: Int = 0,
    val routeReviewPending: Boolean = false,
    val ratePerKm: Double = 0.43,
    val tollsCents: Int = 0,
    val parkingCents: Int = 0,
    val vehicleId: String = "",
    val vehicleName: String = "",
    val registrationPlate: String = "",
    val tripKind: String = TripKinds.BUSINESS,
    val gpsQuality: String = GpsQuality.UNKNOWN,
    val gpsWarning: String = "",
    val calendarEventId: Long? = null,
    val calendarTitle: String = "",
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val displayName: String,
    val mimeType: String,
    val data: ByteArray,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "location_points",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val segmentMeters: Double,
)
