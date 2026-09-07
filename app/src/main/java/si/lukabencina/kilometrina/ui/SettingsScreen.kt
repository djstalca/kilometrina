package si.lukabencina.kilometrina.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.SavedPlace
import si.lukabencina.kilometrina.data.Vehicle
import si.lukabencina.kilometrina.data.VehicleState

@Composable
fun SettingsScreen(
    settings: AppSettings,
    savedPlaces: List<SavedPlace>,
    vehicleState: VehicleState,
    onSave: (Double, String, String, String) -> Unit,
    onAutoDetectionEnabled: (Boolean) -> Unit,
    onSavePlace: (SavedPlace) -> Unit,
    onDeletePlace: (String) -> Unit,
    onSaveVehicle: (Vehicle, Boolean) -> Unit,
    onDeleteVehicle: (String) -> Unit,
    onSetDefaultVehicle: (String) -> Unit,
) {
    var rateText by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf("") }
    var driverName by rememberSaveable { mutableStateOf("") }
    var companyName by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    var editingPlace by remember { mutableStateOf<SavedPlace?>(null) }
    var showNewPlaceDialog by remember { mutableStateOf(false) }
    var deletePlaceCandidate by remember { mutableStateOf<SavedPlace?>(null) }
    var editingVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var showNewVehicleDialog by remember { mutableStateOf(false) }
    var deleteVehicleCandidate by remember { mutableStateOf<Vehicle?>(null) }

    LaunchedEffect(settings) {
        if (!dirty) {
            rateText = String.format(Locale.forLanguageTag("sl-SI"), "%.2f", settings.ratePerKm)
            purpose = settings.defaultPurpose
            driverName = settings.driverName
            companyName = settings.companyName
        }
    }

    val parsedRate = rateText.replace(',', '.').toDoubleOrNull()
    val valid = parsedRate != null && parsedRate in 0.0..10.0 && purpose.isNotBlank()
    val detectionPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            permissionError = null
            onAutoDetectionEnabled(true)
        } else {
            permissionError = "Za pametno zaznavo dovoli prepoznavanje aktivnosti in obvestila. Funkcija ostaja izključena."
            onAutoDetectionEnabled(false)
        }
    }

    fun requestDetectionEnabled() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isEmpty()) onAutoDetectionEnabled(true)
        else detectionPermissionLauncher.launch(permissions.toTypedArray())
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Nastavitve", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("Obračun, avtomatizacija, vozila in hitre lokacije", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsCard("Obračun in poročila") {
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
                modifier = Modifier.fillMaxWidth(), label = { Text("Privzeti namen poti") }, singleLine = true,
            )
            OutlinedTextField(
                value = driverName,
                onValueChange = { driverName = it.take(80); saved = false; dirty = true },
                modifier = Modifier.fillMaxWidth(), label = { Text("Voznik") }, placeholder = { Text("Ime in priimek") }, singleLine = true,
            )
            OutlinedTextField(
                value = companyName,
                onValueChange = { companyName = it.take(100); saved = false; dirty = true },
                modifier = Modifier.fillMaxWidth(), label = { Text("Podjetje (neobvezno)") }, singleLine = true,
            )
            Button(
                onClick = {
                    onSave(parsedRate ?: settings.ratePerKm, purpose, driverName, companyName)
                    dirty = false
                    saved = true
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saved) "Shranjeno" else "Shrani nastavitve") }
        }

        SettingsCard("Pametna zaznava vožnje") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (settings.autoDetectionEnabled) "Vključena" else "Izključena", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Ko telefon zazna verjetno vožnjo, dobiš predlog. Beleženje se nikoli ne začne samodejno.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = settings.autoDetectionEnabled,
                    onCheckedChange = { enabled ->
                        permissionError = null
                        if (enabled) requestDetectionEnabled() else onAutoDetectionEnabled(false)
                    },
                )
            }
            permissionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        SettingsCard("Vozila") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Privzeto vozilo se uporabi pri novi GPS vožnji.",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                IconButton(onClick = { showNewVehicleDialog = true }) { Icon(Icons.Outlined.Add, contentDescription = "Dodaj vozilo") }
            }
            if (vehicleState.vehicles.isEmpty()) {
                Text("Dodaj vozilo, da bo registracija pravilno zapisana v novih vožnjah in poročilih.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                vehicleState.vehicles.forEach { vehicle ->
                    val isDefault = vehicle.id == vehicleState.defaultVehicle?.id
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.DirectionsCar, contentDescription = null)
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(vehicle.name, fontWeight = FontWeight.SemiBold)
                                Text(vehicle.registrationPlate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (isDefault) Text("Privzeto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onSetDefaultVehicle(vehicle.id) }, enabled = !isDefault) {
                                Icon(if (isDefault) Icons.Outlined.Star else Icons.Outlined.StarBorder, contentDescription = if (isDefault) "Privzeto vozilo" else "Nastavi kot privzeto")
                            }
                            IconButton(onClick = { editingVehicle = vehicle }) { Icon(Icons.Outlined.Edit, contentDescription = "Uredi ${vehicle.name}") }
                            IconButton(onClick = { deleteVehicleCandidate = vehicle }) { Icon(Icons.Outlined.Delete, contentDescription = "Izbriši ${vehicle.name}") }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showNewVehicleDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(" Dodaj vozilo")
            }
        }

        SettingsCard("Priljubljene lokacije") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Stranke, pisarna, dom in druge pogoste točke.", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { showNewPlaceDialog = true }) { Icon(Icons.Outlined.Add, contentDescription = "Dodaj priljubljeno lokacijo") }
            }
            savedPlaces.forEach { place ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Place, contentDescription = null)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(place.name, fontWeight = FontWeight.SemiBold)
                            Text(place.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (place.defaultPurpose.isNotBlank()) Text(place.defaultPurpose, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { editingPlace = place }) { Icon(Icons.Outlined.Edit, contentDescription = "Uredi ${place.name}") }
                        IconButton(onClick = { deletePlaceCandidate = place }) { Icon(Icons.Outlined.Delete, contentDescription = "Izbriši ${place.name}") }
                    }
                }
            }
            if (savedPlaces.isEmpty()) Text("Dodaj prvo lokacijo, da jo lahko izbereš z enim dotikom pri ročnem vnosu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { showNewPlaceDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(" Dodaj lokacijo")
            }
        }

        InfoCard(
            icon = { Icon(Icons.Outlined.Route, contentDescription = null) },
            title = "GPS trasa ostane shranjena",
            text = "Pri GPS vožnjah aplikacija shrani sprejete točke. Traso lahko odpreš iz podrobnosti vožnje tudi brez spletnega zemljevida.",
        )
        InfoCard(
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            title = "Podatki ostanejo tvoji",
            text = "Vožnje, vozila in lokacije so lokalni. V zavihku Podatki lahko narediš popolno varnostno kopijo.",
        )
    }

    if (showNewPlaceDialog) {
        SavedPlaceDialog(null, { showNewPlaceDialog = false }) { onSavePlace(it); showNewPlaceDialog = false }
    }
    editingPlace?.let { place ->
        SavedPlaceDialog(place, { editingPlace = null }) { onSavePlace(it); editingPlace = null }
    }
    deletePlaceCandidate?.let { place ->
        AlertDialog(
            onDismissRequest = { deletePlaceCandidate = null },
            title = { Text("Izbrišem ${place.name}?") },
            text = { Text("Lokacija bo odstranjena samo iz priljubljenih. Obstoječe vožnje ostanejo nespremenjene.") },
            confirmButton = { Button(onClick = { onDeletePlace(place.id); deletePlaceCandidate = null }) { Text("Izbriši") } },
            dismissButton = { TextButton(onClick = { deletePlaceCandidate = null }) { Text("Prekliči") } },
        )
    }

    if (showNewVehicleDialog) {
        VehicleDialog(null, false, { showNewVehicleDialog = false }) { vehicle, makeDefault ->
            onSaveVehicle(vehicle, makeDefault)
            showNewVehicleDialog = false
        }
    }
    editingVehicle?.let { vehicle ->
        VehicleDialog(vehicle, vehicle.id == vehicleState.defaultVehicle?.id, { editingVehicle = null }) { updated, makeDefault ->
            onSaveVehicle(updated, makeDefault)
            editingVehicle = null
        }
    }
    deleteVehicleCandidate?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { deleteVehicleCandidate = null },
            title = { Text("Izbrišem ${vehicle.name}?") },
            text = { Text("Vozilo bo odstranjeno iz izbire. Zgodovinske vožnje ohranijo zapisano ime in registracijo.") },
            confirmButton = { Button(onClick = { onDeleteVehicle(vehicle.id); deleteVehicleCandidate = null }) { Text("Izbriši") } },
            dismissButton = { TextButton(onClick = { deleteVehicleCandidate = null }) { Text("Prekliči") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun VehicleDialog(
    vehicle: Vehicle?,
    initiallyDefault: Boolean,
    onDismiss: () -> Unit,
    onSave: (Vehicle, Boolean) -> Unit,
) {
    var name by remember(vehicle?.id) { mutableStateOf(vehicle?.name.orEmpty()) }
    var registration by remember(vehicle?.id) { mutableStateOf(vehicle?.registrationPlate.orEmpty()) }
    var makeDefault by remember(vehicle?.id) { mutableStateOf(initiallyDefault) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (vehicle == null) "Novo vozilo" else "Uredi vozilo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Vozilo") }, placeholder = { Text("npr. VW Passat") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = registration, onValueChange = { registration = it.uppercase().take(24) }, label = { Text("Registrska oznaka") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Privzeto vozilo")
                    Switch(checked = makeDefault, onCheckedChange = { makeDefault = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        Vehicle(
                            id = vehicle?.id ?: Vehicle(name = name, registrationPlate = registration).id,
                            name = name,
                            registrationPlate = registration,
                        ),
                        makeDefault,
                    )
                },
                enabled = name.isNotBlank() && registration.isNotBlank(),
            ) { Text("Shrani") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Prekliči") } },
    )
}

@Composable
private fun SavedPlaceDialog(place: SavedPlace?, onDismiss: () -> Unit, onSave: (SavedPlace) -> Unit) {
    var name by remember(place?.id) { mutableStateOf(place?.name.orEmpty()) }
    var address by remember(place?.id) { mutableStateOf(place?.address.orEmpty()) }
    var defaultPurpose by remember(place?.id) { mutableStateOf(place?.defaultPurpose.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (place == null) "Nova lokacija" else "Uredi lokacijo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Ime ali stranka") }, placeholder = { Text("npr. Prodent") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it.take(160) }, label = { Text("Naslov") }, placeholder = { Text("Ulica, kraj") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = defaultPurpose, onValueChange = { defaultPurpose = it.take(80) }, label = { Text("Privzeti namen (neobvezno)") }, placeholder = { Text("npr. Obisk stranke") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        SavedPlace(
                            id = place?.id ?: SavedPlace(name = name, address = address).id,
                            name = name,
                            address = address,
                            defaultPurpose = defaultPurpose,
                        ),
                    )
                },
                enabled = name.isNotBlank() && address.isNotBlank(),
            ) { Text("Shrani") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Prekliči") } },
    )
}

@Composable
private fun InfoCard(icon: @Composable () -> Unit, title: String, text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            icon()
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
