package si.lukabencina.kilometrina.data

import android.content.Context
import android.location.Geocoder
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import si.lukabencina.kilometrina.location.TrackingMath
import java.util.Locale

class TripRepository(
    private val context: Context,
    private val dao: TripDao,
) {
    val trips: Flow<List<TripEntity>> = dao.observeTrips()
    val activeTrip: Flow<TripEntity?> = dao.observeActiveTrip()

    suspend fun getActiveTrip(): TripEntity? = dao.getActiveTrip()

    suspend fun startTrip(location: Location, purpose: String, ratePerKm: Double): Long {
        val address = reverseGeocode(location.latitude, location.longitude)
        val id = dao.insertTrip(
            TripEntity(
                startTime = System.currentTimeMillis(),
                startLat = location.latitude,
                startLon = location.longitude,
                startAddress = address,
                purpose = purpose.trim().ifBlank { "Službena pot" },
                ratePerKm = ratePerKm,
            ),
        )
        dao.insertPoint(
            LocationPointEntity(
                tripId = id,
                timestamp = System.currentTimeMillis(),
                lat = location.latitude,
                lon = location.longitude,
                accuracyMeters = location.accuracy,
                segmentMeters = 0.0,
            ),
        )
        return id
    }

    suspend fun appendLocation(tripId: Long, location: Location): Double {
        val last = dao.getLastPoint(tripId)
        if (last == null) {
            dao.insertPoint(
                LocationPointEntity(
                    tripId = tripId,
                    timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    lat = location.latitude,
                    lon = location.longitude,
                    accuracyMeters = location.accuracy,
                    segmentMeters = 0.0,
                ),
            )
            return 0.0
        }

        val results = FloatArray(1)
        Location.distanceBetween(last.lat, last.lon, location.latitude, location.longitude, results)
        val segment = results[0].toDouble()
        val elapsedSec = ((location.time.takeIf { it > 0 } ?: System.currentTimeMillis()) - last.timestamp)
            .coerceAtLeast(1L) / 1000.0
        // Ignore GPS jitter, low-accuracy fixes and impossible jumps.
        if (!TrackingMath.shouldAcceptSegment(segment, elapsedSec, location.accuracy)) return 0.0

        dao.insertPoint(
            LocationPointEntity(
                tripId = tripId,
                timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                lat = location.latitude,
                lon = location.longitude,
                accuracyMeters = location.accuracy,
                segmentMeters = segment,
            ),
        )
        dao.addDistance(tripId, segment)
        return segment
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
