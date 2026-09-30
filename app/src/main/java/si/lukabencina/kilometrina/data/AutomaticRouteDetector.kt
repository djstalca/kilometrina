package si.lukabencina.kilometrina.data

/**
 * Detects meaningful stops from the already filtered GPS trace.
 *
 * While the car is stationary, tiny GPS movements are intentionally rejected
 * by TrackingMath. A real stop therefore appears as a time gap between two
 * accepted points that are still geographically close to one another.
 */
object AutomaticRouteDetector {
    private const val MIN_DWELL_MILLIS = 4 * 60 * 1000L
    private const val MAX_DWELL_SPREAD_METERS = 250.0
    private const val ENDPOINT_EXCLUSION_METERS = 300.0
    private const val ROUND_TRIP_RADIUS_METERS = 600.0
    private const val TURNAROUND_MIN_DISTANCE_METERS = 3_000.0
    private const val MERGE_RADIUS_METERS = 300.0

    data class Candidate(
        val lat: Double,
        val lon: Double,
        val timestamp: Long,
        val source: Source,
    )

    enum class Source {
        DWELL,
        TURNAROUND,
    }

    fun detect(points: List<LocationPointEntity>): List<Candidate> {
        val clean = points
            .asSequence()
            .filter { validPoint(it) }
            .sortedBy { it.timestamp }
            .toList()
        if (clean.size < 2) return emptyList()

        val start = clean.first()
        val end = clean.last()
        val candidates = mutableListOf<Candidate>()

        clean.zipWithNext().forEach { (before, after) ->
            val gap = after.timestamp - before.timestamp
            if (gap < MIN_DWELL_MILLIS) return@forEach
            val spread = SavedPlaceRepository.distanceMeters(before.lat, before.lon, after.lat, after.lon)
            if (spread > MAX_DWELL_SPREAD_METERS) return@forEach

            val candidate = Candidate(
                lat = (before.lat + after.lat) / 2.0,
                lon = (before.lon + after.lon) / 2.0,
                timestamp = before.timestamp + gap / 2,
                source = Source.DWELL,
            )
            if (!nearEndpoint(candidate, start) && !nearEndpoint(candidate, end)) {
                addMerged(candidates, candidate)
            }
        }

        val roundTripDistance = SavedPlaceRepository.distanceMeters(start.lat, start.lon, end.lat, end.lon)
        if (roundTripDistance <= ROUND_TRIP_RADIUS_METERS && candidates.isEmpty()) {
            val farthest = clean.maxByOrNull {
                SavedPlaceRepository.distanceMeters(start.lat, start.lon, it.lat, it.lon)
            }
            if (farthest != null) {
                val distanceFromStart = SavedPlaceRepository.distanceMeters(start.lat, start.lon, farthest.lat, farthest.lon)
                if (distanceFromStart >= TURNAROUND_MIN_DISTANCE_METERS) {
                    addMerged(
                        candidates,
                        Candidate(
                            lat = farthest.lat,
                            lon = farthest.lon,
                            timestamp = farthest.timestamp,
                            source = Source.TURNAROUND,
                        ),
                    )
                }
            }
        }

        return candidates.sortedBy { it.timestamp }
    }

    private fun addMerged(target: MutableList<Candidate>, candidate: Candidate) {
        val existingIndex = target.indexOfFirst {
            SavedPlaceRepository.distanceMeters(it.lat, it.lon, candidate.lat, candidate.lon) <= MERGE_RADIUS_METERS
        }
        if (existingIndex < 0) {
            target += candidate
            return
        }

        val existing = target[existingIndex]
        target[existingIndex] = Candidate(
            lat = (existing.lat + candidate.lat) / 2.0,
            lon = (existing.lon + candidate.lon) / 2.0,
            timestamp = minOf(existing.timestamp, candidate.timestamp),
            source = if (existing.source == Source.DWELL || candidate.source == Source.DWELL) Source.DWELL else Source.TURNAROUND,
        )
    }

    private fun nearEndpoint(candidate: Candidate, endpoint: LocationPointEntity): Boolean =
        SavedPlaceRepository.distanceMeters(candidate.lat, candidate.lon, endpoint.lat, endpoint.lon) <= ENDPOINT_EXCLUSION_METERS

    private fun validPoint(point: LocationPointEntity): Boolean =
        point.timestamp > 0L &&
            point.lat.isFinite() && point.lat in -90.0..90.0 &&
            point.lon.isFinite() && point.lon in -180.0..180.0 &&
            point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..50f
}
