package si.lukabencina.kilometrina.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import si.lukabencina.kilometrina.data.AppSettings
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (Double, String) -> Unit,
) {
    var rateText by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings) {
        if (!dirty) {
            rateText = String.format(Locale.forLanguageTag("sl-SI"), "%.2f", settings.ratePerKm)
            purpose = settings.defaultPurpose
        }
    }

    val parsedRate = rateText.replace(',', '.').toDoubleOrNull()
    val valid = parsedRate != null && parsedRate in 0.0..10.0 && purpose.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text("Nastavitve", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("Privzete vrednosti za nove vožnje", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = {
                        rateText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6)
                        saved = false
                        dirty = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Postavka na kilometer") },
                    suffix = { Text("€/km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = rateText.isNotBlank() && parsedRate == null,
                )
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it.take(80); saved = false; dirty = true },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Privzeti namen poti") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        onSave(parsedRate ?: settings.ratePerKm, purpose)
                        dirty = false
                        saved = true
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saved) "Shranjeno" else "Shrani nastavitve")
                }
            }
        }

        InfoCard(
            icon = { Icon(Icons.Outlined.Route, contentDescription = null) },
            title = "Dejanska pot",
            text = "Aplikacija sešteva GPS odseke med vožnjo. Ne računa samo razdalje med začetno in končno točko.",
        )
        InfoCard(
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            title = "Podatki ostanejo na telefonu",
            text = "Vožnje in lokacije se shranijo lokalno. Za prvo verzijo ni uporabniškega računa ali strežniške sinhronizacije.",
        )
    }
}

@Composable
private fun InfoCard(
    icon: @Composable () -> Unit,
    title: String,
    text: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
