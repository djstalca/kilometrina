package si.lukabencina.kilometrina.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.SavedPlace
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    savedPlaces: List<SavedPlace>,
    onSave: (Double, String) -> Unit,
    onSavePlace: (SavedPlace) -> Unit,
    onDeletePlace: (String) -> Unit,
) {
    var rateText by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<SavedPlace?>(null) }
    var showNewPlaceDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<SavedPlace?>(null) }

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
            Text("Privzete vrednosti in hitre lokacije", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Obračun", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Priljubljene lokacije", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Stranke, pisarna, dom in druge pogoste točke.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { showNewPlaceDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Dodaj priljubljeno lokacijo")
                    }
                }

                if (savedPlaces.isEmpty()) {
                    Text(
                        "Dodaj prvo lokacijo, da jo boš lahko izbral z enim dotikom pri ročnem vnosu ali kot hiter namen poti.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { showNewPlaceDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text(" Dodaj lokacijo")
                    }
                } else {
                    savedPlaces.forEach { place ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Place, contentDescription = null)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                ) {
                                    Text(place.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        place.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (place.defaultPurpose.isNotBlank()) {
                                        Text(
                                            place.defaultPurpose,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                IconButton(onClick = { editingPlace = place }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Uredi ${place.name}")
                                }
                                IconButton(onClick = { deleteCandidate = place }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Izbriši ${place.name}")
                                }
                            }
                        }
                    }
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
            text = "Vožnje in priljubljene lokacije se shranijo lokalno. Za trenutno verzijo ni uporabniškega računa ali strežniške sinhronizacije.",
        )
    }

    if (showNewPlaceDialog) {
        SavedPlaceDialog(
            place = null,
            onDismiss = { showNewPlaceDialog = false },
            onSave = {
                onSavePlace(it)
                showNewPlaceDialog = false
            },
        )
    }

    editingPlace?.let { place ->
        SavedPlaceDialog(
            place = place,
            onDismiss = { editingPlace = null },
            onSave = {
                onSavePlace(it)
                editingPlace = null
            },
        )
    }

    deleteCandidate?.let { place ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Izbrišem ${place.name}?") },
            text = { Text("Lokacija bo odstranjena samo iz priljubljenih. Obstoječe vožnje ostanejo nespremenjene.") },
            confirmButton = {
                Button(onClick = {
                    onDeletePlace(place.id)
                    deleteCandidate = null
                }) { Text("Izbriši") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Prekliči") }
            },
        )
    }
}

@Composable
private fun SavedPlaceDialog(
    place: SavedPlace?,
    onDismiss: () -> Unit,
    onSave: (SavedPlace) -> Unit,
) {
    var name by remember(place?.id) { mutableStateOf(place?.name.orEmpty()) }
    var address by remember(place?.id) { mutableStateOf(place?.address.orEmpty()) }
    var defaultPurpose by remember(place?.id) { mutableStateOf(place?.defaultPurpose.orEmpty()) }
    val valid = name.isNotBlank() && address.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (place == null) "Nova lokacija" else "Uredi lokacijo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Ime ali stranka") },
                    placeholder = { Text("npr. Prodent") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.take(160) },
                    label = { Text("Naslov") },
                    placeholder = { Text("Ulica, kraj") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = defaultPurpose,
                    onValueChange = { defaultPurpose = it.take(80) },
                    label = { Text("Privzeti namen (neobvezno)") },
                    placeholder = { Text("npr. Obisk stranke") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
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
                enabled = valid,
            ) { Text("Shrani") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Prekliči") }
        },
    )
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
