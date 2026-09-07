package si.lukabencina.kilometrina.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.savedPlacesDataStore by preferencesDataStore(name = "saved_places")

data class SavedPlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val defaultPurpose: String = "",
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
        context.savedPlacesDataStore.edit { prefs ->
            val current = prefs[placesKey].orEmpty()
                .mapNotNull(SavedPlaceCodec::decode)
                .filterNot { it.id == normalized.id }
                .toMutableList()
            current += normalized
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

    private fun normalize(place: SavedPlace): SavedPlace? {
        val normalized = place.copy(
            id = place.id.trim(),
            name = place.name.trim().take(60),
            address = place.address.trim().take(160),
            defaultPurpose = place.defaultPurpose.trim().take(80),
        )
        return normalized.takeIf { it.id.isNotBlank() && it.name.isNotBlank() && it.address.isNotBlank() }
    }
}

object SavedPlaceCodec {
    fun encode(place: SavedPlace): String = listOf(
        place.id,
        place.name,
        place.address,
        place.defaultPurpose,
    ).joinToString("|") { encodeField(it) }

    fun decode(value: String): SavedPlace? = runCatching {
        val fields = value.split('|')
        if (fields.size != 4) return null
        SavedPlace(
            id = decodeField(fields[0]),
            name = decodeField(fields[1]),
            address = decodeField(fields[2]),
            defaultPurpose = decodeField(fields[3]),
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
