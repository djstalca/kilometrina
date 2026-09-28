package si.lukabencina.kilometrina.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import si.lukabencina.kilometrina.data.AttachmentEntity
import si.lukabencina.kilometrina.data.SavedPlace
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.data.TripKinds
import si.lukabencina.kilometrina.data.TripRouteCodec
import si.lukabencina.kilometrina.data.routeStops
import si.lukabencina.kilometrina.data.TripRules
import si.lukabencina.kilometrina.data.Vehicle
import si.lukabencina.kilometrina.data.VehicleState

private val slLocale = Locale.forLanguageTag("sl-SI")
private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", slLocale)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("d. M. yyyy • HH:mm", slLocale)

@Composable
fun TripsScreen(
    trips: List<TripEntity>,
    defaultRatePerKm: Double,
    savedPlaces: List<SavedPlace>,
    recentLocations: List<String>,
    vehicleState: VehicleState,
    attachments: List<AttachmentEntity>,
    onDeleteTrip: (Long) -> Unit,
    onUpdateTrip: (TripEntity) -> Unit,
    onAddManualTrip: (TripEntity) -> Unit,
    onAddAttachment: (Long, String, String, ByteArray) -> Unit,
    detailViewModel: TripDetailViewModel = viewModel(),
) {
    val context = LocalContext.current
    val routeState by detailViewModel.state.collectAsStateWithLifecycle()
    val completedTrips = remember(trips) { trips.filter { it.endTime != null } }
    var selectedMonthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val selectedMonth = remember(selectedMonthValue) { YearMonth.parse(selectedMonthValue) }
    val monthTrips = remember(completedTrips, selectedMonth) {
        completedTrips.filter {
            val date = Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
            YearMonth.from(date) == selectedMonth
        }
    }
    val businessMonthTrips = remember(monthTrips) { monthTrips.filter { it.tripKind != TripKinds.PRIVATE } }
    val monthKm = businessMonthTrips.sumOf { it.distanceMeters } / 1000.0
    val monthMileage = businessMonthTrips.sumOf(::tripCompensation)
    val monthExtras = businessMonthTrips.sumOf(::tripAdditionalCosts)
    val monthTotal = businessMonthTrips.sumOf(::tripTotalCost)
    var deleteCandidate by remember { mutableStateOf<TripEntity?>(null) }
    var editCandidate by remember { mutableStateOf<TripEntity?>(null) }
    var detailCandidate by remember { mutableStateOf<TripEntity?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var attachmentCandidate by remember { mutableStateOf<TripEntity?>(null) }

    LaunchedEffect(detailCandidate?.id) {
        detailCandidate?.let { detailViewModel.load(it.id) }
    }

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

    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val trip = attachmentCandidate
        attachmentCandidate = null
        if (uri != null && trip != null) {
            runCatching {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val name = attachmentDisplayName(context, uri)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Priloge ni bilo mogoče prebrati.")
                require(bytes.size <= 10 * 1024 * 1024) { "Priloga je večja od 10 MB." }
                onAddAttachment(trip.id, name, mime, bytes)
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
                    Text("Dotakni se vožnje za podrobnosti in GPS traso", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { selectedMonthValue = selectedMonth.minusMonths(1).toString() }) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Prejšnji mesec")
                        }
                        Text(
                            selectedMonth.atDay(1).format(monthFormatter).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(
                            onClick = { selectedMonthValue = selectedMonth.plusMonths(1).toString() },
                            enabled = selectedMonth < YearMonth.now(),
                        ) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Naslednji mesec")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SummaryMetric("Službene", businessMonthTrips.size.toString(), Modifier.weight(1f))
                        SummaryMetric("Kilometri", String.format(slLocale, "%.1f km", monthKm), Modifier.weight(1f))
                        SummaryMetric("Kilometrina", formatMoney(monthMileage), Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    CostLine("Parkirnine in cestnine", monthExtras)
                    CostLine("Skupaj povračilo", monthTotal, emphasized = true)
                }
            }
        }

        if (monthTrips.isEmpty()) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("V tem mesecu še ni voženj", fontWeight = FontWeight.SemiBold)
                        Text("Začni GPS vožnjo na domačem zaslonu ali jo dodaj ročno.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { showManualDialog = true }) { Text("Dodaj ročno") }
                    }
                }
            }
        } else {
            items(monthTrips, key = { it.id }) { trip ->
                TripRow(
                    trip = trip,
                    attachmentCount = attachments.count { it.tripId == trip.id },
                    onOpen = { detailCandidate = trip },
                    onEdit = { editCandidate = trip },
                    onAttach = {
                        attachmentCandidate = trip
                        attachmentLauncher.launch(arrayOf("image/*", "application/pdf"))
                    },
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
            vehicleState = vehicleState,
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
            vehicleState = vehicleState,
            onDismiss = { editCandidate = null },
            onSave = {
                onUpdateTrip(it)
                editCandidate = null
                selectedMonthValue = YearMonth.from(fromEpochMillis(it.startTime)).toString()
            },
        )
    }

    detailCandidate?.let { trip ->
        TripDetailDialog(
            trip = trip,
            routeState = routeState,
            onDeleteAttachment = detailViewModel::deleteAttachment,
            onDismiss = {
                detailCandidate = null
                detailViewModel.clear()
            },
        )
    }

    deleteCandidate?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Izbrišem vožnjo?") },
            text = { Text("Izbrisane vožnje in njene GPS trase ni mogoče povrniti brez varnostne kopije.") },
            confirmButton = {
                Button(onClick = { onDeleteTrip(trip.id); deleteCandidate = null }) { Text("Izbriši") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Prekliči") } },
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal)
        Text(formatMoney(value), fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun TripRow(
    trip: TripEntity,
    attachmentCount: Int,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onAttach: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tripDisplayTitle(trip), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (trip.description.isNotBlank()) {
                        Text(
                            trip.purpose,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        "${formatDate(trip.startTime)} • ${formatTime(trip.startTime)}–${trip.endTime?.let(::formatTime).orEmpty()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (trip.registrationPlate.isNotBlank()) {
                        Text(
                            listOf(trip.vehicleName, trip.registrationPlate).filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onAttach) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = "Dodaj prilogo")
                }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Uredi vožnjo") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Izbriši vožnjo") }
            }
            if (trip.tripKind == TripKinds.PRIVATE) {
                Text("Zasebna vožnja", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            if (attachmentCount > 0) {
                Text("$attachmentCount prilog", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                formatTripRoute(trip),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Route, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(formatKm(trip.distanceMeters), fontWeight = FontWeight.Medium)
                }
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
    vehicleState: VehicleState,
    onDismiss: () -> Unit,
    onSave: (TripEntity) -> Unit,
) {
    val now = remember { LocalDateTime.now().withSecond(0).withNano(0) }
    var startDateTime by remember { mutableStateOf(now.minusHours(1)) }
    var endDateTime by remember { mutableStateOf(now) }
    var purpose by remember { mutableStateOf("Službena pot") }
    var description by remember { mutableStateOf("") }
    var tripKind by remember { mutableStateOf(TripKinds.BUSINESS) }
    var startAddress by remember { mutableStateOf("") }
    var endAddress by remember { mutableStateOf("") }
    var routeStops by remember { mutableStateOf(emptyList<String>()) }
    var distanceKm by remember { mutableStateOf("") }
    var ratePerKm by remember { mutableStateOf(decimalInput(defaultRatePerKm, 2)) }
    var parking by remember { mutableStateOf("") }
    var tolls by remember { mutableStateOf("") }
    var selectedVehicleId by remember { mutableStateOf(vehicleState.defaultVehicle?.id.orEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    TripEditorDialog(
        title = "Dodaj vožnjo",
        confirmLabel = "Dodaj",
        onDismiss = onDismiss,
        onConfirm = {
            val values = validateForm(startDateTime, endDateTime, distanceKm, ratePerKm, parking, tolls)
            if (values == null) {
                errorMessage = "Preveri čas in številčne vrednosti. Prihod mora biti po odhodu."
                return@TripEditorDialog
            }
            val vehicle = vehicleState.vehicles.firstOrNull { it.id == selectedVehicleId }
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
                    description = description.trim(),
                    routeStopsJson = TripRouteCodec.encode(routeStops),
                    ratePerKm = values.ratePerKm,
                    parkingCents = (values.parking * 100.0).roundToInt(),
                    tollsCents = (values.tolls * 100.0).roundToInt(),
                    vehicleId = vehicle?.id.orEmpty(),
                    vehicleName = vehicle?.name.orEmpty(),
                    registrationPlate = vehicle?.registrationPlate.orEmpty(),
                    tripKind = tripKind,
                ),
            )
        },
    ) {
        TripFormFields(
            tripKind = tripKind,
            onTripKindChange = { tripKind = it },
            purpose = purpose,
            onPurposeChange = { purpose = it },
            description = description,
            onDescriptionChange = { description = it },
            startAddress = startAddress,
            onStartAddressChange = { startAddress = it },
            endAddress = endAddress,
            onEndAddressChange = { endAddress = it },
            routeStops = routeStops,
            onRouteStopsChange = { routeStops = it },
            savedPlaces = savedPlaces,
            recentLocations = recentLocations,
            onEndSavedPlace = {
                endAddress = it.address
                if (it.defaultPurpose.isNotBlank() && purpose.equals("Službena pot", ignoreCase = true)) {
                    purpose = it.defaultPurpose
                }
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
            vehicles = vehicleState.vehicles,
            selectedVehicleId = selectedVehicleId,
            onVehicleSelected = { selectedVehicleId = it },
            errorMessage = errorMessage,
        )
    }
}

@Composable
private fun EditTripDialog(
    trip: TripEntity,
    savedPlaces: List<SavedPlace>,
    recentLocations: List<String>,
    vehicleState: VehicleState,
    onDismiss: () -> Unit,
    onSave: (TripEntity) -> Unit,
) {
    var startDateTime by remember(trip.id) { mutableStateOf(fromEpochMillis(trip.startTime)) }
    var endDateTime by remember(trip.id) { mutableStateOf(fromEpochMillis(requireNotNull(trip.endTime))) }
    var purpose by remember(trip.id) { mutableStateOf(trip.purpose) }
    var description by remember(trip.id) { mutableStateOf(trip.description) }
    var tripKind by remember(trip.id) { mutableStateOf(trip.tripKind) }
    var startAddress by remember(trip.id) { mutableStateOf(trip.startAddress) }
    var endAddress by remember(trip.id) { mutableStateOf(trip.endAddress.orEmpty()) }
    var routeStops by remember(trip.id) { mutableStateOf(trip.routeStops()) }
    var distanceKm by remember(trip.id) { mutableStateOf(decimalInput(trip.distanceMeters / 1000.0, 1)) }
    var ratePerKm by remember(trip.id) { mutableStateOf(decimalInput(trip.ratePerKm, 2)) }
    var parking by remember(trip.id) { mutableStateOf(decimalInput(trip.parkingCents / 100.0, 2)) }
    var tolls by remember(trip.id) { mutableStateOf(decimalInput(trip.tollsCents / 100.0, 2)) }
    val snapshotVehicle = remember(trip.id) {
        if (trip.vehicleName.isNotBlank() && vehicleState.vehicles.none { it.id == trip.vehicleId }) {
            Vehicle(id = trip.vehicleId.ifBlank { "snapshot-${trip.id}" }, name = trip.vehicleName, registrationPlate = trip.registrationPlate)
        } else null
    }
    val vehicleOptions = remember(vehicleState.vehicles, snapshotVehicle) {
        buildList { snapshotVehicle?.let(::add); addAll(vehicleState.vehicles) }.distinctBy { it.id }
    }
    var selectedVehicleId by remember(trip.id) { mutableStateOf(trip.vehicleId.ifBlank { vehicleState.defaultVehicle?.id.orEmpty() }) }
    var errorMessage by remember(trip.id) { mutableStateOf<String?>(null) }

    TripEditorDialog(
        title = "Uredi vožnjo",
        confirmLabel = "Shrani",
        onDismiss = onDismiss,
        onConfirm = {
            val values = validateForm(startDateTime, endDateTime, distanceKm, ratePerKm, parking, tolls)
            if (values == null) {
                errorMessage = "Preveri čas in številčne vrednosti. Prihod mora biti po odhodu."
                return@TripEditorDialog
            }
            val vehicle = vehicleOptions.firstOrNull { it.id == selectedVehicleId }
            onSave(
                trip.copy(
                    startTime = toEpochMillis(startDateTime),
                    endTime = toEpochMillis(endDateTime),
                    purpose = purpose.trim().ifBlank { "Službena pot" },
                    description = description.trim(),
                    routeStopsJson = TripRouteCodec.encode(routeStops),
                    startAddress = startAddress.trim().ifBlank { "Lokacija ni na voljo" },
                    endAddress = endAddress.trim().ifBlank { "Lokacija ni na voljo" },
                    distanceMeters = values.distanceKm * 1000.0,
                    ratePerKm = values.ratePerKm,
                    parkingCents = (values.parking * 100.0).roundToInt(),
                    tollsCents = (values.tolls * 100.0).roundToInt(),
                    vehicleId = vehicle?.id.orEmpty(),
                    vehicleName = vehicle?.name.orEmpty(),
                    registrationPlate = vehicle?.registrationPlate.orEmpty(),
                    tripKind = tripKind,
                ),
            )
        },
    ) {
        TripFormFields(
            tripKind = tripKind,
            onTripKindChange = { tripKind = it },
            purpose = purpose,
            onPurposeChange = { purpose = it },
            description = description,
            onDescriptionChange = { description = it },
            startAddress = startAddress,
            onStartAddressChange = { startAddress = it },
            endAddress = endAddress,
            onEndAddressChange = { endAddress = it },
            routeStops = routeStops,
            onRouteStopsChange = { routeStops = it },
            savedPlaces = savedPlaces,
            recentLocations = recentLocations,
            onEndSavedPlace = {
                endAddress = it.address
                if (it.defaultPurpose.isNotBlank() && purpose.equals("Službena pot", ignoreCase = true)) {
                    purpose = it.defaultPurpose
                }
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
            vehicles = vehicleOptions,
            selectedVehicleId = selectedVehicleId,
            onVehicleSelected = { selectedVehicleId = it },
            errorMessage = errorMessage,
        )
    }
}

@Composable
private fun TripEditorDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column {
                Text(
                    title,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                HorizontalDivider()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    content()
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Prekliči") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm) { Text(confirmLabel) }
                }
            }
        }
    }
}

@Composable
private fun TripFormFields(
    tripKind: String,
    onTripKindChange: (String) -> Unit,
    purpose: String,
    onPurposeChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    startAddress: String,
    onStartAddressChange: (String) -> Unit,
    endAddress: String,
    onEndAddressChange: (String) -> Unit,
    routeStops: List<String>,
    onRouteStopsChange: (List<String>) -> Unit,
    savedPlaces: List<SavedPlace>,
    recentLocations: List<String>,
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
    vehicles: List<Vehicle>,
    selectedVehicleId: String,
    onVehicleSelected: (String) -> Unit,
    errorMessage: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tripKind == TripKinds.BUSINESS,
                onClick = { onTripKindChange(TripKinds.BUSINESS) },
                label = { Text("Službena") },
            )
            FilterChip(
                selected = tripKind == TripKinds.PRIVATE,
                onClick = { onTripKindChange(TripKinds.PRIVATE) },
                label = { Text("Zasebna") },
            )
        }

        DateTimeField("Odhod", startDateTime, onStartDateTimeChange)
        DateTimeField("Prihod", endDateTime, onEndDateTimeChange)

        OutlinedTextField(
            value = purpose,
            onValueChange = { onPurposeChange(it.take(200)) },
            label = { Text("Namen poti") },
            supportingText = { Text("Razlog službene poti, npr. servis, montaža ali obisk stranke.") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = description,
            onValueChange = { onDescriptionChange(it.take(500)) },
            label = { Text("Opis vožnje") },
            supportingText = { Text("Kratek opis, kaj si na tej vožnji opravil.") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )

        Text("Relacija", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LocationField(
            label = "Lokacija odhoda",
            value = startAddress,
            onValueChange = onStartAddressChange,
            savedPlaces = savedPlaces,
            recentLocations = recentLocations,
        )

        routeStops.forEachIndexed { index, stop ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    LocationField(
                        label = "Postanek ${index + 1}",
                        value = stop,
                        onValueChange = { value ->
                            onRouteStopsChange(routeStops.toMutableList().also { it[index] = value })
                        },
                        savedPlaces = savedPlaces,
                        recentLocations = recentLocations,
                    )
                }
                Column {
                    IconButton(
                        onClick = {
                            val updated = routeStops.toMutableList()
                            val item = updated.removeAt(index)
                            updated.add(index - 1, item)
                            onRouteStopsChange(updated)
                        },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Premakni postanek gor")
                    }
                    IconButton(
                        onClick = {
                            val updated = routeStops.toMutableList()
                            val item = updated.removeAt(index)
                            updated.add(index + 1, item)
                            onRouteStopsChange(updated)
                        },
                        enabled = index < routeStops.lastIndex,
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Premakni postanek dol")
                    }
                    IconButton(
                        onClick = { onRouteStopsChange(routeStops.toMutableList().also { it.removeAt(index) }) },
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Odstrani postanek")
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { onRouteStopsChange(routeStops + "") },
            enabled = routeStops.size < TripRouteCodec.MAX_STOPS,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Dodaj postanek")
        }

        LocationField(
            label = "Lokacija prihoda",
            value = endAddress,
            onValueChange = onEndAddressChange,
            savedPlaces = savedPlaces,
            recentLocations = recentLocations,
            onSavedPlace = onEndSavedPlace,
        )

        Text("Vozilo", style = MaterialTheme.typography.labelLarge)
        if (vehicles.isEmpty()) {
            Text(
                "Ni dodanega vozila. Dodaš ga lahko v Nastavitvah.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vehicles, key = { it.id }) { vehicle ->
                    FilterChip(
                        selected = vehicle.id == selectedVehicleId,
                        onClick = { onVehicleSelected(vehicle.id) },
                        label = { Text(if (vehicle.registrationPlate.isBlank()) vehicle.name else "${vehicle.name} • ${vehicle.registrationPlate}") },
                    )
                }
            }
        }

        OutlinedTextField(value = distanceKm, onValueChange = onDistanceKmChange, label = { Text("Kilometri") }, suffix = { Text("km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = ratePerKm, onValueChange = onRatePerKmChange, label = { Text("Postavka") }, suffix = { Text("€/km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = parking, onValueChange = onParkingChange, label = { Text("Parkirnina") }, suffix = { Text("€") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = tolls, onValueChange = onTollsChange, label = { Text("Cestnina") }, suffix = { Text("€") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun LocationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    savedPlaces: List<SavedPlace>,
    recentLocations: List<String>,
    onSavedPlace: (SavedPlace) -> Unit = { onValueChange(it.address) },
) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.take(TripRouteCodec.MAX_ADDRESS_LENGTH)) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            singleLine = true,
        )
        if (focused) {
            QuickLocationRow(
                query = value,
                savedPlaces = savedPlaces,
                recentLocations = recentLocations,
                onSavedPlace = onSavedPlace,
                onRecentLocation = onValueChange,
            )
        }
    }
}

@Composable
private fun QuickLocationRow(
    query: String,
    savedPlaces: List<SavedPlace>,
    recentLocations: List<String>,
    onSavedPlace: (SavedPlace) -> Unit,
    onRecentLocation: (String) -> Unit,
) {
    val normalizedQuery = query.trim().lowercase()
    val saved = remember(savedPlaces, normalizedQuery) {
        savedPlaces
            .filter {
                normalizedQuery.isBlank() ||
                    it.name.lowercase().contains(normalizedQuery) ||
                    it.address.lowercase().contains(normalizedQuery)
            }
            .take(5)
    }
    val savedAddresses = remember(savedPlaces) { savedPlaces.map { it.address.trim().lowercase() }.toSet() }
    val recent = remember(recentLocations, savedAddresses, normalizedQuery) {
        recentLocations
            .map(String::trim)
            .filter { it.isNotBlank() && !isRawCoordinateLocation(it) }
            .filterNot { it.lowercase() in savedAddresses }
            .filter { normalizedQuery.isBlank() || it.lowercase().contains(normalizedQuery) }
            .distinct()
            .take(5)
    }
    if (saved.isEmpty() && recent.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(saved, key = { "saved-${it.id}" }) { place ->
            SuggestionChip(onClick = { onSavedPlace(place) }, label = { Text(place.name) })
        }
        items(recent, key = { "recent-$it" }) { address ->
            SuggestionChip(onClick = { onRecentLocation(address) }, label = { Text(shortLocation(address)) })
        }
    }
}

private val rawCoordinateLocation = Regex(
    """^\s*-?\d{1,3}(?:[.,]\d+)?\s*,\s*-?\d{1,3}(?:[.,]\d+)?\s*$""",
)

private fun isRawCoordinateLocation(value: String): Boolean = rawCoordinateLocation.matches(value)

@Composable
private fun DateTimeField(label: String, value: LocalDateTime, onValueChange: (LocalDateTime) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val selectedDate = LocalDateTime.of(year, month + 1, day, value.hour, value.minute)
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onValueChange(selectedDate.withHour(hour).withMinute(minute)) },
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
        ) { Text(value.format(dateTimeFormatter)) }
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
    if (kmValue == null || rateValue == null || parkingValue == null || tollsValue == null || kmValue < 0.0 || rateValue < 0.0 || parkingValue < 0.0 || tollsValue < 0.0) return null
    return ValidatedTripValues(kmValue, rateValue, parkingValue, tollsValue)
}

private fun toEpochMillis(value: LocalDateTime): Long = value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun fromEpochMillis(value: Long): LocalDateTime = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDateTime()
private fun parseDecimal(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()
private fun parseOptionalDecimal(value: String): Double? = if (value.isBlank()) 0.0 else parseDecimal(value)
private fun decimalInput(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value).trimEnd('0').trimEnd('.')


private fun attachmentDisplayName(context: android.content.Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.take(180) else null
    } ?: uri.lastPathSegment?.takeLast(120) ?: "Priloga"
}
