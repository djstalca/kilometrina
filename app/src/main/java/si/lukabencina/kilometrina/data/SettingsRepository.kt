package si.lukabencina.kilometrina.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val ratePerKm: Double = 0.43,
    val defaultPurpose: String = "Službena pot",
)

class SettingsRepository(private val context: Context) {
    private val rateKey = doublePreferencesKey("rate_per_km")
    private val purposeKey = stringPreferencesKey("default_purpose")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            ratePerKm = prefs[rateKey] ?: 0.43,
            defaultPurpose = prefs[purposeKey] ?: "Službena pot",
        )
    }

    suspend fun setRatePerKm(value: Double) {
        context.dataStore.edit { it[rateKey] = value.coerceIn(0.0, 10.0) }
    }

    suspend fun setDefaultPurpose(value: String) {
        context.dataStore.edit { it[purposeKey] = value.trim().ifBlank { "Službena pot" } }
    }
}
