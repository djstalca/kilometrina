package si.lukabencina.kilometrina.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vehiclesDataStore by preferencesDataStore(name = "vehicles")

data class Vehicle(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val registrationPlate: String,
)

data class VehicleState(
    val vehicles: List<Vehicle> = emptyList(),
    val defaultVehicleId: String = "",
) {
    val defaultVehicle: Vehicle?
        get() = vehicles.firstOrNull { it.id == defaultVehicleId } ?: vehicles.firstOrNull()
}

class VehicleRepository(private val context: Context) {
    private val vehiclesKey = stringSetPreferencesKey("vehicles")
    private val defaultVehicleKey = stringPreferencesKey("default_vehicle_id")

    val state: Flow<VehicleState> = context.vehiclesDataStore.data.map { prefs ->
        val vehicles = prefs[vehiclesKey]
            .orEmpty()
            .mapNotNull(VehicleCodec::decode)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val requestedDefault = prefs[defaultVehicleKey].orEmpty()
        VehicleState(
            vehicles = vehicles,
            defaultVehicleId = requestedDefault.takeIf { id -> vehicles.any { it.id == id } }
                ?: vehicles.firstOrNull()?.id.orEmpty(),
        )
    }

    suspend fun upsert(vehicle: Vehicle, makeDefault: Boolean = false) {
        val normalized = vehicle.copy(
            name = vehicle.name.trim().take(80),
            registrationPlate = vehicle.registrationPlate.trim().uppercase().take(24),
        )
        if (normalized.name.isBlank() || normalized.registrationPlate.isBlank()) return
        context.vehiclesDataStore.edit { prefs ->
            val current = prefs[vehiclesKey].orEmpty()
                .mapNotNull(VehicleCodec::decode)
                .filterNot { it.id == normalized.id }
                .toMutableList()
            current += normalized
            prefs[vehiclesKey] = current.map(VehicleCodec::encode).toSet()
            val hasDefault = current.any { it.id == prefs[defaultVehicleKey] }
            if (makeDefault || !hasDefault) prefs[defaultVehicleKey] = normalized.id
        }
    }

    suspend fun delete(id: String) {
        context.vehiclesDataStore.edit { prefs ->
            val remaining = prefs[vehiclesKey].orEmpty()
                .mapNotNull(VehicleCodec::decode)
                .filterNot { it.id == id }
            prefs[vehiclesKey] = remaining.map(VehicleCodec::encode).toSet()
            if (prefs[defaultVehicleKey] == id) {
                val next = remaining.firstOrNull()?.id
                if (next == null) prefs.remove(defaultVehicleKey) else prefs[defaultVehicleKey] = next
            }
        }
    }

    suspend fun setDefault(id: String) {
        context.vehiclesDataStore.edit { prefs ->
            val exists = prefs[vehiclesKey].orEmpty().mapNotNull(VehicleCodec::decode).any { it.id == id }
            if (exists) prefs[defaultVehicleKey] = id
        }
    }

    suspend fun seedLegacyIfEmpty(name: String, registrationPlate: String) {
        val cleanName = name.trim()
        val cleanPlate = registrationPlate.trim().uppercase()
        if (cleanName.isBlank() || cleanPlate.isBlank()) return
        context.vehiclesDataStore.edit { prefs ->
            if (prefs[vehiclesKey].orEmpty().isNotEmpty()) return@edit
            val legacy = Vehicle(id = "legacy-default", name = cleanName, registrationPlate = cleanPlate)
            prefs[vehiclesKey] = setOf(VehicleCodec.encode(legacy))
            prefs[defaultVehicleKey] = legacy.id
        }
    }

    suspend fun replaceAll(state: VehicleState) {
        val clean = state.vehicles
            .map {
                it.copy(
                    name = it.name.trim().take(80),
                    registrationPlate = it.registrationPlate.trim().uppercase().take(24),
                )
            }
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.registrationPlate.isNotBlank() }
            .distinctBy { it.id }
        context.vehiclesDataStore.edit { prefs ->
            prefs[vehiclesKey] = clean.map(VehicleCodec::encode).toSet()
            val defaultId = state.defaultVehicleId.takeIf { id -> clean.any { it.id == id } }
                ?: clean.firstOrNull()?.id
            if (defaultId == null) prefs.remove(defaultVehicleKey) else prefs[defaultVehicleKey] = defaultId
        }
    }
}

object VehicleCodec {
    fun encode(vehicle: Vehicle): String = listOf(
        vehicle.id,
        vehicle.name,
        vehicle.registrationPlate,
    ).joinToString("|") { encodeField(it) }

    fun decode(value: String): Vehicle? = runCatching {
        val fields = value.split('|')
        if (fields.size != 3) return null
        Vehicle(
            id = decodeField(fields[0]),
            name = decodeField(fields[1]),
            registrationPlate = decodeField(fields[2]),
        ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() && it.registrationPlate.isNotBlank() }
    }.getOrNull()

    private fun encodeField(value: String): String {
        if (value.isEmpty()) return "e"
        return "b" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeField(value: String): String = when {
        value == "e" -> ""
        value.startsWith("b") -> String(
            Base64.getUrlDecoder().decode(value.drop(1)),
            StandardCharsets.UTF_8,
        )
        else -> error("Invalid vehicle field")
    }
}
