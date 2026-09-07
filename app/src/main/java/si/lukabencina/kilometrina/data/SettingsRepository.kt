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
    val driverName: String = "",
    val companyName: String = "",
    val vehicleName: String = "",
    val registrationPlate: String = "",
)

class SettingsRepository(private val context: Context) {
    private val rateKey = doublePreferencesKey("rate_per_km")
    private val purposeKey = stringPreferencesKey("default_purpose")
    private val driverNameKey = stringPreferencesKey("driver_name")
    private val companyNameKey = stringPreferencesKey("company_name")
    private val vehicleNameKey = stringPreferencesKey("vehicle_name")
    private val registrationPlateKey = stringPreferencesKey("registration_plate")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            ratePerKm = prefs[rateKey] ?: 0.43,
            defaultPurpose = prefs[purposeKey] ?: "Službena pot",
            driverName = prefs[driverNameKey].orEmpty(),
            companyName = prefs[companyNameKey].orEmpty(),
            vehicleName = prefs[vehicleNameKey].orEmpty(),
            registrationPlate = prefs[registrationPlateKey].orEmpty(),
        )
    }

    suspend fun setRatePerKm(value: Double) {
        context.dataStore.edit { it[rateKey] = value.coerceIn(0.0, 10.0) }
    }

    suspend fun setDefaultPurpose(value: String) {
        context.dataStore.edit { it[purposeKey] = value.trim().ifBlank { "Službena pot" } }
    }

    suspend fun setReportProfile(
        driverName: String,
        companyName: String,
        vehicleName: String,
        registrationPlate: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[driverNameKey] = driverName.trim().take(80)
            prefs[companyNameKey] = companyName.trim().take(100)
            prefs[vehicleNameKey] = vehicleName.trim().take(100)
            prefs[registrationPlateKey] = registrationPlate.trim().uppercase().take(24)
        }
    }

    suspend fun replaceAll(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[rateKey] = settings.ratePerKm.coerceIn(0.0, 10.0)
            prefs[purposeKey] = settings.defaultPurpose.trim().ifBlank { "Službena pot" }.take(80)
            prefs[driverNameKey] = settings.driverName.trim().take(80)
            prefs[companyNameKey] = settings.companyName.trim().take(100)
            prefs[vehicleNameKey] = settings.vehicleName.trim().take(100)
            prefs[registrationPlateKey] = settings.registrationPlate.trim().uppercase().take(24)
        }
    }
}
