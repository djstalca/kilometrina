package si.lukabencina.kilometrina.data

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val savedPlaceRepository: SavedPlaceRepository,
    private val settingsRepository: SettingsRepository,
    private val calendarSuggestionRepository: CalendarSuggestionRepository,
) {
    val trips: Flow<List<TripEntity>> = dao.observeTrips()
    val activeTrip: Flow<TripEntity?> = dao.observeActiveTrip()
    val attachments: Flow<List<AttachmentEntity>> = dao.observeAttachments()

    suspend fun getActiveTrip(): TripEntity? = dao.getActiveTrip()
    suspend fun getRoutePoints(tripId: Long): List<LocationPointEntity> = dao.getPointsForTrip(tripId)
    suspend fun getAttachments(tripId: Long): List<AttachmentEntity> = dao.getAttachmentsForTrip(tripId)

    suspend fun addAttachment(tripId: Long, displayName: String, mimeType: String, data: ByteArray): Long {
        require(data.isNotEmpty()) { "Priloga je prazna." }
        require(data.size <= 10 * 1024 * 1024) { "Priloga je večja od 10 MB." }
        val cleanMime = mimeType.trim().ifBlank { "application/octet-stream" }.take(120)
        require(cleanMime == "application/pdf" || cleanMime.startsWith("image/")) { "Podprte so slike in PDF dokumenti." }
        return dao.insertAttachment(
            AttachmentEntity(
                tripId = tripId,
                displayName = displayName.trim().ifBlank { "Priloga" }.take(180),
                mimeType = cleanMime,
                data = data,
            ),
        )
    }

    suspend fun deleteAttachment(id: Long) = dao.deleteAttachment(id)

    suspend fun startTrip(
        location: Location,
        purpose: String,
        ratePerKm: Double,
        vehicle: Vehicle?,
        tripKind: String = TripKinds.BUSINESS,
    ): Long {
        val address = smartLocationLabel(location.latitude, location.longitude)
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
                tripKind = normalizeTripKind(tripKind),
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
                purpose = trip.purpose.trim().ifBlank { "Službena pot" }.take(200),
                description = trip.description.trim().take(500),
                routeStopsJson = TripRouteCodec.normalize(trip.routeStopsJson),
                distanceMeters = trip.distanceMeters.coerceAtLeast(0.0),
                ratePerKm = trip.ratePerKm.coerceAtLeast(0.0),
                tollsCents = trip.tollsCents.coerceAtLeast(0),
                parkingCents = trip.parkingCents.coerceAtLeast(0),
                vehicleName = trip.vehicleName.trim().take(80),
                registrationPlate = trip.registrationPlate.trim().uppercase().take(24),
                tripKind = normalizeTripKind(trip.tripKind),
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

        val matchedPlace = finalLocation?.let {
            savedPlaceRepository.nearestPlace(it.latitude, it.longitude)?.place
        }
        val address = finalLocation?.let {
            matchedPlace?.name ?: reverseGeocode(it.latitude, it.longitude)
        }
        val endTime = System.currentTimeMillis()
        val genericPurpose = active.purpose.trim().equals("Službena pot", ignoreCase = true)
        val savedPlacePurpose = matchedPlace?.defaultPurpose?.takeIf { it.isNotBlank() && genericPurpose }
        val settings = settingsRepository.settings.first()
        val calendarSuggestion = if (settings.calendarIntegrationEnabled && genericPurpose && savedPlacePurpose == null) {
            calendarSuggestionRepository.findBestSuggestion(active.startTime, endTime)
        } else {
            null
        }
        val smartPurpose = savedPlacePurpose ?: calendarSuggestion?.title ?: active.purpose
        val gpsAssessment = assessGps(dao.getPointsForTrip(active.id))

        dao.updateTrip(
            active.copy(
                endTime = endTime,
                endLat = finalLocation?.latitude,
                endLon = finalLocation?.longitude,
                endAddress = address ?: "Lokacija ni na voljo",
                purpose = smartPurpose,
                gpsQuality = gpsAssessment.quality,
                gpsWarning = gpsAssessment.warning,
                calendarEventId = calendarSuggestion?.eventId,
                calendarTitle = calendarSuggestion?.title.orEmpty(),
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
                purpose = trip.purpose.trim().ifBlank { "Službena pot" }.take(200),
                description = trip.description.trim().take(500),
                routeStopsJson = TripRouteCodec.normalize(trip.routeStopsJson),
                distanceMeters = trip.distanceMeters.coerceAtLeast(0.0),
                ratePerKm = trip.ratePerKm.coerceAtLeast(0.0),
                tollsCents = trip.tollsCents.coerceAtLeast(0),
                parkingCents = trip.parkingCents.coerceAtLeast(0),
                vehicleName = trip.vehicleName.trim().take(80),
                registrationPlate = trip.registrationPlate.trim().uppercase().take(24),
                tripKind = normalizeTripKind(trip.tripKind),
            ),
        )
    }

    suspend fun deleteTrip(id: Long) = dao.deleteTrip(id)

    private data class GpsAssessment(val quality: String, val warning: String)

    private fun assessGps(points: List<LocationPointEntity>): GpsAssessment {
        if (points.size < 2) return GpsAssessment(GpsQuality.POOR, "GPS trasa nima dovolj točk.")
        val sorted = points.sortedBy { it.timestamp }
        val averageAccuracy = sorted.map { it.accuracyMeters.toDouble() }.average()
        val maxGapSeconds = sorted.zipWithNext { a, b -> ((b.timestamp - a.timestamp).coerceAtLeast(0L) / 1000L) }
            .maxOrNull() ?: 0L
        return when {
            maxGapSeconds > 120L || averageAccuracy > 35.0 ->
                GpsAssessment(GpsQuality.POOR, "Preveri traso: zaznan je daljši GPS izpad ali slabša natančnost.")
            maxGapSeconds > 45L || averageAccuracy > 20.0 ->
                GpsAssessment(GpsQuality.FAIR, "GPS zapis je uporaben, vendar ni povsem enakomeren.")
            else -> GpsAssessment(GpsQuality.GOOD, "")
        }
    }

    private fun normalizeTripKind(value: String): String =
        if (value == TripKinds.PRIVATE) TripKinds.PRIVATE else TripKinds.BUSINESS

    private suspend fun smartLocationLabel(lat: Double, lon: Double): String =
        savedPlaceRepository.displayNameFor(lat, lon) ?: reverseGeocode(lat, lon)

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
