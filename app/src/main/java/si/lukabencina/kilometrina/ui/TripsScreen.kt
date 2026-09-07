package si.lukabencina.kilometrina.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import si.lukabencina.kilometrina.data.SavedPlace
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.data.TripRules
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val slLocale = Locale.forLanguageTag("sl-SI")
private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", slLocale)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("d. M. yyyy • HH:mm", slLocale)

@Composable
fun TripsScreen(
    trips: List<TripEntity>,
    defaultRatePerKm: Double,
    savedPlaces: List<SavedPlace>,
    recentLocations: List<String>,
    onDeleteTrip: (Long) -> Unit,
    onUpdateTrip: (TripEntity) -> Unit,
    onAddManualTrip: (TripEntity) -> Unit,
) {
    val context = LocalContext.current
    val completedTrips = remember(trips) { trips.filter { it.endTime != null } }
    var selectedMonthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val selectedMonth = remember(selectedMonthValue) { YearMonth.parse(selectedMonthValue) }
    val monthTrips = remember(completedTrips, selectedMonth) {
        completedTrips.filter {
            val date = Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
            YearMonth.from(date) == selectedMonth
        }
    }
    val monthKm = monthTrips.sumOf { it.distanceMeters } / 1000.0
    val monthMileage = monthTrips.sumOf(::tripCompensation)
    val monthExtras = monthTrips.sumOf(::tripAdditionalCosts)
    val monthTotal = monthTrips.sumOf(::tripTotalCost)
    var deleteCandidate by remember { mutableStateOf<TripEntity?>(null) }
    var editCandidate by remember { mutableStateOf<TripEntity?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write("\uFEFF")
                it.write(CsvExporter.build(monthTrips))
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Vožnje", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text("Pregled in mesečni obračun", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = { showManualDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Dodaj vožnjo ročno")
                    }
                    IconButton(
                        onClick = { exportLauncher.launch("kilometrina-${selectedMonth}.csv") },
                        enabled = monthTrips.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Izvozi izbrani mesec")
                    }
                }
            }
        }

        item {
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
                        IconButton(onClick = {
                            selectedMonthValue = selectedMonth.minusMonths(1).toString()
                        }) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Prejšnji mesec")
                        }
                        Text(
                            selectedMonth.atDay(1).format(monthFormatter).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(onClick = {
                            selectedMonthValue = selectedMonth.plusMonths(1).toString()
                        }) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Naslednji mesec")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SummaryMetric("Vožnje", monthTrips.size.toString(), Modifier.weight(1f))
                        SummaryMetric("Kilometri", String.format(slLocale, "%.1f km", monthKm), Modifier.weight(1f))
                        SummaryMetric("Kilometrina", formatMoney(monthMileage), Modifier.weight(1f))
                    }

                    HorizontalDivider()
                    CostLine("Parkirnine in cestnine", monthExtras)
                    CostLine("Skupaj povračilo", monthTotal, emphasized = true)

                    OutlinedButton(
                        onClick = { exportLauncher.launch("kilometrina-${selectedMonth}.csv") },
                        enabled = monthTrips.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Izvozi CSV")
                    }
                }
            }
        }

        if (monthTrips.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        "V izbranem mesecu še ni zaključenih voženj.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(monthTrips, key = { it.id }) { trip ->
                TripRow(
                    trip = trip,
                    onEdit = { editCandidate = trip },
                    onDelete = { deleteCandidate = trip },
                )
            }
        }
    }

    if (showManualDialog) {
        ManualTripDialog(
            defaultRatePerKm = defaultRatePerKm,
            savedPlaces = savedPlaces,
            recentLocations = recentLocations,
            onDismiss = { showManualDialog = false },
            onSave = {
                onAddManualTrip(it)
                showManualDialog = false
                selectedMonthValue = YearMonth.from(fromEpochMillis(it.startTime)).toString()
            },
        )
    }

    editCandidate?.let { trip ->
        EditTripDialog(
            trip = trip,
            savedPlaces = savedPlaces,
            recentLocations = recentLocations,
            onDismiss = { editCandidate = null },
            onSave = {
                onUpdateTrip(it)
                editCandidate = null
                selectedMonthValue = YearMonth.from(fromEpochMillis(it.startTime)).toString()
            },
        )
    }

    deleteCandidate?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Izbrišem vožnjo?") },
            text = { Text("Izbrisane vožnje ni mogoče povrniti.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteTrip(trip.id)
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
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CostLine(label: String, value: Double, emphasized: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(formatMoney(value), fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun TripRow(trip: TripEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(trip.purpose, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatDate(trip.startTime)} • ${formatTime(trip.startTime)}–${trip.endTime?.let(::formatTime).orEmpty()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Uredi vožnjo")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Izbriši vožnjo")
                }
            }
            Text("${shortLocation(trip.startAddress)} → ${shortLocation(trip.endAddress)}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(formatKm(trip.distanceMeters), fontWeight = FontWeight.Medium)
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMoney(tripTotalCost(trip)), fontWeight = FontWeight.SemiBold)
                    if (tripAdditionalCosts(trip) > 0.0) {
                        Text(
                            "${formatMoney(tripCompensation(trip))} + ${formatMoney(tripAdditionalCosts(trip))} stroškov",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualTripDialog(
    defaultRatePerKm: Double,
    savedPlaces: List<SavedPlace>,
    recentLocations: List<String>,
    onDismiss: () -> Unit,
    onSave: (TripEntity) -> Unit,
) {
    val now = remember { LocalDateTime.now().withSecond(0).withNano(0) }
    var startDateTime by remember { mutableStateOf(now.minusHours(1)) }
    var endDateTime by remember { mutableStateOf(now) }
    var purpose by remember { mutableStateOf("Službena pot") }
    var startAddress by remember { mutableStateOf("") }
    var endAddress by remember { mutableStateOf("") }
    var distanceKm by remember { mutableStateOf("") }
    var ratePerKm by remember { mutableStateOf(decimalInput(defaultRatePerKm, 2)) }
    var parking by remember { mutableStateOf("") }
    var tolls by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj vožnjo ročno") },
        text = {
            TripFormFields(
                purpose = purpose,
                onPurposeChange = { purpose = it },
                startAddress = startAddress,
                onStartAddressChange = { startAddress = it },
                endAddress = endAddress,
                onEndAddressChange = { endAddress = it },
                savedPlaces = savedPlaces,
                recentLocations = recentLocations,
                onStartSavedPlace = { startAddress = it.address },
                onEndSavedPlace = {
                    endAddress = it.address
                    if (it.defaultPurpose.isNotBlank()) purpose = it.defaultPurpose
                },
                startDateTime = startDateTime,
                onStartDateTimeChange = { startDateTime = it },
                endDateTime = endDateTime,
                onEndDateTimeChange = { endDateTime = it },
                distanceKm = distanceKm,
                onDistanceKmChange = { distanceKm = it },
                ratePerKm = ratePerKm,
                onRatePerKmChange = { ratePerKm = it },
                parking = parking,
                onParkingChange = { parking = it },
                tolls = tolls,
                onTollsChange = { tolls = it },
                errorMessage = errorMessage,
            )
        },
        confirmButton = {
            Button(onClick = {
                val values = validateForm(
                    startDateTime = startDateTime,
                    endDateTime = endDateTime,
                    distanceKm = distanceKm,
                    ratePerKm = ratePerKm,
                    parking = parking,
                    tolls = tolls,
                )
                if (values == null) {
                    errorMessage = "Preveri čas in številčne vrednosti. Prihod mora biti po odhodu."
                    return@Button
                }
                onSave(
                    TripEntity(
                        startTime = toEpochMillis(startDateTime),
                        endTime = toEpochMillis(endDateTime),
                        startLat = 0.0,
                        startLon = 0.0,
                        startAddress = startAddress.trim().ifBlank { "Lokacija ni na voljo" },
                        endAddress = endAddress.trim().ifBlank { "Lokacija ni na voljo" },
                        distanceMeters = values.distanceKm * 1000.0,
                        purpose = purpose.trim().ifBlank { "Službena pot" },
                        ratePerKm = values.ratePerKm,
                        parkingCents = (values.parking * 100.0).roundToInt(),
                        tollsCents = (values.tolls * 100.0).roundToInt(),
                    ),
                )
            }) { Text("Dodaj") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Prekliči") }
        },
    )
}

@Composable
private fun EditTripDialog(
    trip: TripEntity,
    savedPlaces: List<SavedPlace>,
    recentLocations: List<String>,
    onDismiss: () -> Unit,
    onSave: (TripEntity) -> Unit,
) {
    var startDateTime by remember(trip.id) { mutableStateOf(fromEpochMillis(trip.startTime)) }
    var endDateTime by remember(trip.id) { mutableStateOf(fromEpochMillis(requireNotNull(trip.endTime))) }
    var purpose by remember(trip.id) { mutableStateOf(trip.purpose) }
    var startAddress by remember(trip.id) { mutableStateOf(trip.startAddress) }
    var endAddress by remember(trip.id) { mutableStateOf(trip.endAddress.orEmpty()) }
    var distanceKm by remember(trip.id) { mutableStateOf(decimalInput(trip.distanceMeters / 1000.0, 1)) }
    var ratePerKm by remember(trip.id) { mutableStateOf(decimalInput(trip.ratePerKm, 2)) }
    var parking by remember(trip.id) { mutableStateOf(decimalInput(trip.parkingCents / 100.0, 2)) }
    var tolls by remember(trip.id) { mutableStateOf(decimalInput(trip.tollsCents / 100.0, 2)) }
    var errorMessage by remember(trip.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uredi vožnjo") },
        text = {
            TripFormFields(
                purpose = purpose,
                onPurposeChange = { purpose = it },
                startAddress = startAddress,
                onStartAddressChange = { startAddress = it },
                endAddress = endAddress,
                onEndAddressChange = { endAddress = it },
                savedPlaces = savedPlaces,
                recentLocations = recentLocations,
                onStartSavedPlace = { startAddress = it.address },
                onEndSavedPlace = {
                    endAddress = it.address
                    if (it.defaultPurpose.isNotBlank()) purpose = it.defaultPurpose
                },
                startDateTime = startDateTime,
                onStartDateTimeChange = { startDateTime = it },
                endDateTime = endDateTime,
                onEndDateTimeChange = { endDateTime = it },
                distanceKm = distanceKm,
                onDistanceKmChange = { distanceKm = it },
                ratePerKm = ratePerKm,
                onRatePerKmChange = { ratePerKm = it },
                parking = parking,
                onParkingChange = { parking = it },
                tolls = tolls,
                onTollsChange = { tolls = it },
                errorMessage = errorMessage,
            )
        },
        confirmButton = {
            Button(onClick = {
                val values = validateForm(
                    startDateTime = startDateTime,
                    endDateTime = endDateTime,
                    distanceKm = distanceKm,
                    ratePerKm = ratePerKm,
                    parking = parking,
                    tolls = tolls,
                )
                if (values == null) {
                    errorMessage = "Preveri čas in številčne vrednosti. Prihod mora biti po odhodu."
                    return@Button
                }
                onSave(
                    trip.copy(
                        startTime = toEpochMillis(startDateTime),
                        endTime = toEpochMillis(endDateTime),
                        purpose = purpose.trim().ifBlank { "Službena pot" },
                        startAddress = startAddress.trim().ifBlank { "Lokacija ni na voljo" },
                        endAddress = endAddress.trim().ifBlank { "Lokacija ni na voljo" },
                        distanceMeters = values.distanceKm * 1000.0,
                        ratePerKm = values.ratePerKm,
                        parkingCents = (values.parking * 100.0).roundToInt(),
                        tollsCents = (values.tolls * 100.0).roundToInt(),
                    ),
                )
            }) { Text("Shrani") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Prekliči") }
        },
    )
}

@Composable
private fun TripFormFields(
    purpose: String,
    onPurposeChange: (String) -> Unit,
    startAddress: String,
    onStartAddressChange: (String) -> Unit,
    endAddress: String,
    onEndAddressChange: (String) -> Unit,
    savedPlaces: List<SavedPlace>,
    recentLocations: List<String>,
    onStartSavedPlace: (SavedPlace) -> Unit,
    onEndSavedPlace: (SavedPlace) -> Unit,
    startDateTime: LocalDateTime,
    onStartDateTimeChange: (LocalDateTime) -> Unit,
    endDateTime: LocalDateTime,
    onEndDateTimeChange: (LocalDateTime) -> Unit,
    distanceKm: String,
    onDistanceKmChange: (String) -> Unit,
    ratePerKm: String,
    onRatePerKmChange: (String) -> Unit,
    parking: String,
    onParkingChange: (String) -> Unit,
    tolls: String,
    onTollsChange: (String) -> Unit,
    errorMessage: String?,
) {
    Column(
        modifier = Modifier
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DateTimeField("Odhod", startDateTime, onStartDateTimeChange)
        DateTimeField("Prihod", endDateTime, onEndDateTimeChange)
        OutlinedTextField(
            value = purpose,
            onValueChange = onPurposeChange,
            label = { Text("Namen poti") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = startAddress,
            onValueChange = onStartAddressChange,
            label = { Text("Lokacija odhoda") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        QuickLocationRow(
            savedPlaces = savedPlaces,
            recentLocations = recentLocations,
            onSavedPlace = onStartSavedPlace,
            onRecentLocation = onStartAddressChange,
        )
        OutlinedTextField(
            value = endAddress,
            onValueChange = onEndAddressChange,
            label = { Text("Lokacija prihoda") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        QuickLocationRow(
            savedPlaces = savedPlaces,
            recentLocations = recentLocations,
            onSavedPlace = onEndSavedPlace,
            onRecentLocation = onEndAddressChange,
        )
        OutlinedTextField(
            value = distanceKm,
            onValueChange = onDistanceKmChange,
            label = { Text("Kilometri") },
            suffix = { Text("km") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = ratePerKm,
            onValueChange = onRatePerKmChange,
            label = { Text("Postavka") },
            suffix = { Text("€/km") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = parking,
            onValueChange = onParkingChange,
            label = { Text("Parkirnina") },
            suffix = { Text("€") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = tolls,
            onValueChange = onTollsChange,
            label = { Text("Cestnina") },
            suffix = { Text("€") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuickLocationRow(
    savedPlaces: List<SavedPlace>,
    recentLocations: List<String>,
    onSavedPlace: (SavedPlace) -> Unit,
    onRecentLocation: (String) -> Unit,
) {
    val savedAddresses = remember(savedPlaces) { savedPlaces.map { it.address.trim().lowercase() }.toSet() }
    val recent = remember(recentLocations, savedAddresses) {
        recentLocations.filterNot { it.trim().lowercase() in savedAddresses }.take(4)
    }
    if (savedPlaces.isEmpty() && recent.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(savedPlaces.take(6), key = { "saved-${it.id}" }) { place ->
            SuggestionChip(
                onClick = { onSavedPlace(place) },
                label = { Text(place.name) },
            )
        }
        items(recent, key = { "recent-$it" }) { address ->
            SuggestionChip(
                onClick = { onRecentLocation(address) },
                label = { Text(shortLocation(address)) },
            )
        }
    }
}

@Composable
private fun DateTimeField(
    label: String,
    value: LocalDateTime,
    onValueChange: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val selectedDate = LocalDateTime.of(
                            year,
                            month + 1,
                            day,
                            value.hour,
                            value.minute,
                        )
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                onValueChange(selectedDate.withHour(hour).withMinute(minute))
                            },
                            value.hour,
                            value.minute,
                            true,
                        ).show()
                    },
                    value.year,
                    value.monthValue - 1,
                    value.dayOfMonth,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(value.format(dateTimeFormatter))
        }
    }
}

private data class ValidatedTripValues(
    val distanceKm: Double,
    val ratePerKm: Double,
    val parking: Double,
    val tolls: Double,
)

private fun validateForm(
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
    distanceKm: String,
    ratePerKm: String,
    parking: String,
    tolls: String,
): ValidatedTripValues? {
    val start = toEpochMillis(startDateTime)
    val end = toEpochMillis(endDateTime)
    if (!TripRules.hasValidTimeRange(start, end)) return null

    val kmValue = parseDecimal(distanceKm)
    val rateValue = parseDecimal(ratePerKm)
    val parkingValue = parseOptionalDecimal(parking)
    val tollsValue = parseOptionalDecimal(tolls)
    if (
        kmValue == null || rateValue == null || parkingValue == null || tollsValue == null ||
        kmValue < 0.0 || rateValue < 0.0 || parkingValue < 0.0 || tollsValue < 0.0
    ) return null

    return ValidatedTripValues(kmValue, rateValue, parkingValue, tollsValue)
}

private fun toEpochMillis(value: LocalDateTime): Long =
    value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun fromEpochMillis(value: Long): LocalDateTime =
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDateTime()

private fun parseDecimal(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()

private fun parseOptionalDecimal(value: String): Double? =
    if (value.isBlank()) 0.0 else parseDecimal(value)

private fun decimalInput(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value).trimEnd('0').trimEnd('.')
