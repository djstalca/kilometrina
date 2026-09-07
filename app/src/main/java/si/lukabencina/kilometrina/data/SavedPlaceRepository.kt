package si.lukabencina.kilometrina.data

import android.content.Context
import android.location.Geocoder
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.savedPlacesDataStore by preferencesDataStore(name = "saved_places")

data class SavedPlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val defaultPurpose: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val matchRadiusMeters: Int = 250,
) {
    val hasCoordinates: Boolean
        get() = lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}

data class SavedPlaceMatch(
    val place: SavedPlace,
    val distanceMeters: Double,
)

class SavedPlaceRepository(private val context: Context) {
    private val placesKey = stringSetPreferencesKey("places")

    val places: Flow<List<SavedPlace>> = context.savedPlacesDataStore.data.map { prefs ->
        prefs[placesKey]
            .orEmpty()
            .mapNotNull(SavedPlaceCodec::decode)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    suspend fun upsert(place: SavedPlace) {
        val normalized = normalize(place) ?: return
        val enriched = if (normalized.hasCoordinates) normalized else resolveCoordinates(normalized)
        context.savedPlacesDataStore.edit { prefs ->
            val current = prefs[placesKey].orEmpty()
                .mapNotNull(SavedPlaceCodec::decode)
                .filterNot { it.id == enriched.id }
                .toMutableList()
            current += enriched
            prefs[placesKey] = current.map(SavedPlaceCodec::encode).toSet()
        }
    }

    suspend fun delete(id: String) {
        context.savedPlacesDataStore.edit { prefs ->
            prefs[placesKey] = prefs[placesKey]
                .orEmpty()
                .mapNotNull(SavedPlaceCodec::decode)
                .filterNot { it.id == id }
                .map(SavedPlaceCodec::encode)
                .toSet()
        }
    }

    suspend fun replaceAll(places: List<SavedPlace>) {
        val normalized = places.mapNotNull(::normalize).distinctBy { it.id }
        context.savedPlacesDataStore.edit { prefs ->
            prefs[placesKey] = normalized.map(SavedPlaceCodec::encode).toSet()
        }
    }

    suspend fun resolveMissingCoordinates() {
        val current = places.first()
        val missing = current.filterNot { it.hasCoordinates }
        if (missing.isEmpty()) return
        val resolved = current.map { place ->
            if (place.hasCoordinates) place else resolveCoordinates(place)
        }
        replaceAll(resolved)
    }

    suspend fun nearestPlace(lat: Double, lon: Double): SavedPlaceMatch? {
        if (!validLat(lat) || !validLon(lon)) return null
        return nearestPlace(places.first(), lat, lon)
    }

    suspend fun displayNameFor(lat: Double, lon: Double): String? = nearestPlace(lat, lon)?.place?.name

    private suspend fun resolveCoordinates(place: SavedPlace): SavedPlace = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocationName(place.address, 1)
                ?.firstOrNull()
                ?.let { result ->
                    if (validLat(result.latitude) && validLon(result.longitude)) {
                        place.copy(lat = result.latitude, lon = result.longitude)
                    } else place
                }
        }.getOrNull() ?: place
    }

    private fun normalize(place: SavedPlace): SavedPlace? {
        val normalized = place.copy(
            id = place.id.trim(),
            name = place.name.trim().take(60),
            address = place.address.trim().take(160),
            defaultPurpose = place.defaultPurpose.trim().take(80),
            lat = place.lat?.takeIf(::validLat),
            lon = place.lon?.takeIf(::validLon),
            matchRadiusMeters = place.matchRadiusMeters.coerceIn(100, 1500),
        )
        return normalized.takeIf { it.id.isNotBlank() && it.name.isNotBlank() && it.address.isNotBlank() }
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun nearestPlace(places: List<SavedPlace>, lat: Double, lon: Double): SavedPlaceMatch? {
            if (!validLat(lat) || !validLon(lon)) return null
            return places.asSequence()
                .filter { it.hasCoordinates }
                .map { place ->
                    SavedPlaceMatch(
                        place = place,
                        distanceMeters = distanceMeters(
                            lat,
                            lon,
                            requireNotNull(place.lat),
                            requireNotNull(place.lon),
                        ),
                    )
                }
                .filter { it.distanceMeters <= it.place.matchRadiusMeters }
                .minByOrNull { it.distanceMeters }
        }

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }

        private fun validLat(value: Double): Boolean = value.isFinite() && value in -90.0..90.0
        private fun validLon(value: Double): Boolean = value.isFinite() && value in -180.0..180.0
    }
}

object SavedPlaceCodec {
    fun encode(place: SavedPlace): String = listOf(
        place.id,
        place.name,
        place.address,
        place.defaultPurpose,
        place.lat?.toString().orEmpty(),
        place.lon?.toString().orEmpty(),
        place.matchRadiusMeters.toString(),
    ).joinToString("|") { encodeField(it) }

    fun decode(value: String): SavedPlace? = runCatching {
        val fields = value.split('|')
        if (fields.size != 4 && fields.size != 7) return null
        SavedPlace(
            id = decodeField(fields[0]),
            name = decodeField(fields[1]),
            address = decodeField(fields[2]),
            defaultPurpose = decodeField(fields[3]),
            lat = fields.getOrNull(4)?.let(::decodeField)?.toDoubleOrNull(),
            lon = fields.getOrNull(5)?.let(::decodeField)?.toDoubleOrNull(),
            matchRadiusMeters = fields.getOrNull(6)?.let(::decodeField)?.toIntOrNull()?.coerceIn(100, 1500) ?: 250,
        ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() && it.address.isNotBlank() }
    }.getOrNull()

    private fun encodeField(value: String): String {
        if (value.isEmpty()) return "e"
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return "b$encoded"
    }

    private fun decodeField(value: String): String = when {
        value == "e" -> ""
        value.startsWith("b") -> String(
            Base64.getUrlDecoder().decode(value.drop(1)),
            StandardCharsets.UTF_8,
        )
        else -> error("Invalid saved-place field")
    }
}
