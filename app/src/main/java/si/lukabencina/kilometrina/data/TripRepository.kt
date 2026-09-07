package si.lukabencina.kilometrina.data

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import si.lukabencina.kilometrina.location.SegmentEvaluation
import si.lukabencina.kilometrina.location.TrackingMath
import java.util.Locale

data class LocationAppendResult(
    val addedMeters: Double,
    val evaluation: SegmentEvaluation? = null,
)

class TripRepository(
    private val context: Context,
    private val dao: TripDao,
) {
    val trips: Flow<List<TripEntity>> = dao.observeTrips()
    val activeTrip: Flow<TripEntity?> = dao.observeActiveTrip()

    suspend fun getActiveTrip(): TripEntity? = dao.getActiveTrip()
    suspend fun getRoutePoints(tripId: Long): List<LocationPointEntity> = dao.getPointsForTrip(tripId)

    suspend fun startTrip(
        location: Location,
        purpose: String,
        ratePerKm: Double,
        vehicle: Vehicle?,
    ): Long {
        val address = reverseGeocode(location.latitude, location.longitude)
        val timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val id = dao.insertTrip(
            TripEntity(
                startTime = System.currentTimeMillis(),
                startLat = location.latitude,
                startLon = location.longitude,
                startAddress = address,
                purpose = purpose.trim().ifBlank { "Službena pot" },
                ratePerKm = ratePerKm,
                vehicleId = vehicle?.id.orEmpty(),
                vehicleName = vehicle?.name.orEmpty(),
                registrationPlate = vehicle?.registrationPlate.orEmpty(),
            ),
        )
        dao.insertPoint(
            LocationPointEntity(
                tripId = id,
                timestamp = timestamp,
                lat = location.latitude,
                lon = location.longitude,
                accuracyMeters = location.accuracy,
                segmentMeters = 0.0,
            ),
        )
        return id
    }

    suspend fun addManualTrip(trip: TripEntity): Long {
        val endTime = requireNotNull(trip.endTime) { "Ročna vožnja mora imeti čas prihoda." }
        require(endTime > trip.startTime) { "Čas prihoda mora biti po času odhoda." }

        return dao.insertTrip(
            trip.copy(
                id = 0,
                startLat = 0.0,
                startLon = 0.0,
                endLat = null,
                endLon = null,
                startAddress = trip.startAddress.trim().ifBlank { "Lokacija ni na voljo" },
                endAddress = trip.endAddress?.trim()?.ifBlank { "Lokacija ni na voljo" },
                purpose = trip.purpose.trim().ifBlank { "Službena pot" },
                distanceMeters = trip.distanceMeters.coerceAtLeast(0.0),
                ratePerKm = trip.ratePerKm.coerceAtLeast(0.0),
                tollsCents = trip.tollsCents.coerceAtLeast(0),
                parkingCents = trip.parkingCents.coerceAtLeast(0),
                vehicleName = trip.vehicleName.trim().take(80),
                registrationPlate = trip.registrationPlate.trim().uppercase().take(24),
            ),
        )
    }

    suspend fun appendLocation(tripId: Long, location: Location): LocationAppendResult {
        val timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val last = dao.getLastPoint(tripId)
        if (last == null) {
            dao.insertPoint(
                LocationPointEntity(
                    tripId = tripId,
                    timestamp = timestamp,
                    lat = location.latitude,
                    lon = location.longitude,
                    accuracyMeters = location.accuracy,
                    segmentMeters = 0.0,
                ),
            )
            return LocationAppendResult(addedMeters = 0.0)
        }

        val results = FloatArray(1)
        Location.distanceBetween(last.lat, last.lon, location.latitude, location.longitude, results)
        val segment = results[0].toDouble()
        val elapsedSec = (timestamp - last.timestamp).coerceAtLeast(1L) / 1000.0
        val fixAgeSec = if (location.elapsedRealtimeNanos > 0L) {
            ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos).coerceAtLeast(0L)) / 1_000_000_000.0
        } else {
            0.0
        }
        val evaluation = TrackingMath.evaluateSegment(
            distanceMeters = segment,
            elapsedSeconds = elapsedSec,
            accuracyMeters = location.accuracy,
            previousAccuracyMeters = last.accuracyMeters,
            fixAgeSeconds = fixAgeSec,
        )
        if (!evaluation.accepted) {
            return LocationAppendResult(addedMeters = 0.0, evaluation = evaluation)
        }

        dao.insertPoint(
            LocationPointEntity(
                tripId = tripId,
                timestamp = timestamp,
                lat = location.latitude,
                lon = location.longitude,
                accuracyMeters = location.accuracy,
                segmentMeters = segment,
            ),
        )
        dao.addDistance(tripId, segment)
        return LocationAppendResult(addedMeters = segment, evaluation = evaluation)
    }

    suspend fun finishTrip(location: Location?) {
        val active = dao.getActiveTrip() ?: return
        val finalLocation = location ?: dao.getLastPoint(active.id)?.let { point ->
            Location("stored").apply {
                latitude = point.lat
                longitude = point.lon
            }
        }

        val address = finalLocation?.let { reverseGeocode(it.latitude, it.longitude) }
        dao.updateTrip(
            active.copy(
                endTime = System.currentTimeMillis(),
                endLat = finalLocation?.latitude,
                endLon = finalLocation?.longitude,
                endAddress = address ?: "Lokacija ni na voljo",
            ),
        )
    }

    suspend fun updateCompletedTrip(trip: TripEntity) {
        val endTime = trip.endTime ?: return
        if (endTime <= trip.startTime) return
        dao.updateTrip(
            trip.copy(
                startAddress = trip.startAddress.trim().ifBlank { "Lokacija ni na voljo" },
                endAddress = trip.endAddress?.trim()?.ifBlank { "Lokacija ni na voljo" },
                purpose = trip.purpose.trim().ifBlank { "Službena pot" },
                distanceMeters = trip.distanceMeters.coerceAtLeast(0.0),
                ratePerKm = trip.ratePerKm.coerceAtLeast(0.0),
                tollsCents = trip.tollsCents.coerceAtLeast(0),
                parkingCents = trip.parkingCents.coerceAtLeast(0),
                vehicleName = trip.vehicleName.trim().take(80),
                registrationPlate = trip.registrationPlate.trim().uppercase().take(24),
            ),
        )
    }

    suspend fun deleteTrip(id: Long) = dao.deleteTrip(id)

    private suspend fun reverseGeocode(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(lat, lon, 1)
                ?.firstOrNull()
                ?.let { address ->
                    listOfNotNull(
                        address.thoroughfare,
                        address.subLocality ?: address.locality,
                        address.countryName,
                    ).distinct().joinToString(", ")
                }
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: String.format(Locale.US, "%.5f, %.5f", lat, lon)
    }
}
