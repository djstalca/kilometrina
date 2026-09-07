package si.lukabencina.kilometrina.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.delay
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.location.GpsSignalEvaluator
import si.lukabencina.kilometrina.location.GpsSignalQuality
import si.lukabencina.kilometrina.location.SegmentRejectionReason
import si.lukabencina.kilometrina.location.TrackingDiagnosticsState

private enum class PendingLocationAction {
    Start,
    Resume,
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onStart: (String, (StartState) -> Unit) -> Unit,
    onStop: () -> Unit,
    onResumeRecovered: ((StartState) -> Unit) -> Unit,
    onFinishRecovered: () -> Unit,
    onOpenTrips: () -> Unit,
) {
    val context = LocalContext.current
    var purpose by rememberSaveable { mutableStateOf("") }
    var lastAppliedDefaultPurpose by rememberSaveable { mutableStateOf("") }
    var startState by remember { mutableStateOf<StartState>(StartState.Idle) }
    var pendingAction by remember { mutableStateOf<PendingLocationAction?>(null) }
    var pendingDisclosureAction by remember { mutableStateOf<PendingLocationAction?>(null) }
    var showStopDialog by remember { mutableStateOf(false) }

    val purposeSuggestions = remember(uiState.savedPlaces, uiState.recentPurposes) {
        buildList {
            uiState.savedPlaces
                .filter { it.defaultPurpose.isNotBlank() }
                .forEach { add(it.name to it.defaultPurpose) }
            uiState.recentPurposes.forEach { add(it to it) }
        }.distinctBy { it.second.lowercase() }.take(6)
    }

    LaunchedEffect(uiState.settings.defaultPurpose) {
        if (purpose.isBlank() || purpose == lastAppliedDefaultPurpose) {
            purpose = uiState.settings.defaultPurpose
        }
        lastAppliedDefaultPurpose = uiState.settings.defaultPurpose
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val locationGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val action = pendingAction
        pendingAction = null
        if (!locationGranted) {
            startState = StartState.Error("Za zanesljivo kilometrino izberi natančno lokacijo.")
            return@rememberLauncherForActivityResult
        }
        when (action) {
            PendingLocationAction.Start -> onStart(purpose) { startState = it }
            PendingLocationAction.Resume -> onResumeRecovered { startState = it }
            null -> Unit
        }
    }

    fun launchPermissions(action: PendingLocationAction) {
        pendingAction = action
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    fun requestLocation(action: PendingLocationAction) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            pendingDisclosureAction = action
        } else {
            launchPermissions(action)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Kilometrina",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    uiState.recoveryRequired -> "Nedokončana vožnja potrebuje potrditev."
                    uiState.activeTrip == null -> "Službene poti brez ročnega zapisovanja."
                    else -> "Vožnja se trenutno beleži."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedContent(targetState = uiState.activeTrip != null, label = "trip-state") { hasActiveTrip ->
            if (!hasActiveTrip) {
                ReadyCard(
                    purpose = purpose,
                    onPurposeChange = { purpose = it },
                    purposeSuggestions = purposeSuggestions,
                    ratePerKm = uiState.settings.ratePerKm,
                    startState = startState,
                    onStart = { requestLocation(PendingLocationAction.Start) },
                )
            } else {
                ActiveTripCard(
                    trip = requireNotNull(uiState.activeTrip),
                    diagnostics = uiState.trackingDiagnostics,
                    recoveryRequired = uiState.recoveryRequired,
                    recoveryState = startState,
                    onResume = { requestLocation(PendingLocationAction.Resume) },
                    onFinishRecovered = onFinishRecovered,
                    onStop = { showStopDialog = true },
                )
            }
        }

        uiState.recentTrip?.let { recent ->
            RecentTripCard(recent, onOpenTrips)
        }
    }

    pendingDisclosureAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDisclosureAction = null },
            title = { Text("Lokacija med vožnjo") },
            text = {
                Text(
                    "Kilometrina uporablja natančno lokacijo za merjenje dejansko prevožene poti. Ko začneš vožnjo, lokacijo beleži tudi, ko je zaslon ugasnjen ali aplikacija ni v ospredju. Med sledenjem je vedno prikazano trajno Android obvestilo. GPS podatki ostanejo lokalno na tvoji napravi in se ne uporabljajo za oglase ali analitiko.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingDisclosureAction = null
                    launchPermissions(action)
                }) {
                    Text("Nadaljuj")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDisclosureAction = null }) {
                    Text("Ne zdaj")
                }
            },
        )
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Končam vožnjo?") },
            text = { Text("Shranim trenutno lokacijo kot cilj in zaključim beleženje kilometrov.") },
            confirmButton = {
                Button(onClick = {
                    showStopDialog = false
                    onStop()
                }) { Text("Končaj vožnjo") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Prekliči") }
            },
        )
    }
}

@Composable
private fun ReadyCard(
    purpose: String,
    onPurposeChange: (String) -> Unit,
    purposeSuggestions: List<Pair<String, String>>,
    ratePerKm: Double,
    startState: StartState,
    onStart: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Pripravljen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "GPS začne beležiti po pritisku gumba.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        Icons.Outlined.Navigation,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            OutlinedTextField(
                value = purpose,
                onValueChange = onPurposeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Namen poti") },
                singleLine = true,
            )

            if (purposeSuggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Hitri nameni",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(purposeSuggestions, key = { "${it.first}|${it.second}" }) { suggestion ->
                            SuggestionChip(
                                onClick = { onPurposeChange(suggestion.second) },
                                label = { Text(suggestion.first) },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Postavka", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format(Locale.forLanguageTag("sl-SI"), "%.2f €/km", ratePerKm), fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = onStart,
                enabled = startState !is StartState.Locating,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                if (startState is StartState.Locating) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Pridobivam lokacijo …")
                } else {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Začni vožnjo")
                }
            }

            if (startState is StartState.Error) {
                Text(
                    startState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ActiveTripCard(
    trip: TripEntity,
    diagnostics: TrackingDiagnosticsState,
    recoveryRequired: Boolean,
    recoveryState: StartState,
    onResume: () -> Unit,
    onFinishRecovered: () -> Unit,
    onStop: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsedRealtimeNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(trip.id, recoveryRequired) {
        while (!recoveryRequired) {
            now = System.currentTimeMillis()
            elapsedRealtimeNow = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val minutes = ((now - trip.startTime).coerceAtLeast(0L) / 60_000L)

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        Icons.Outlined.Navigation,
                        contentDescription = null,
                        modifier = Modifier.padding(11.dp).size(26.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        if (recoveryRequired) "Nedokončana vožnja" else "Vožnja v teku",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(trip.purpose, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                }
            }

            if (!recoveryRequired) {
                GpsHealthRow(diagnostics, elapsedRealtimeNow)
            }

            Text(
                formatKm(trip.distanceMeters),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("Začetek", formatTime(trip.startTime), Modifier.weight(1f))
                Metric("Čas", "$minutes min", Modifier.weight(1f))
                Metric("Znesek", formatMoney(tripCompensation(trip)), Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Odhod", style = MaterialTheme.typography.labelLarge)
                Text(shortLocation(trip.startAddress), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }

            if (recoveryRequired) {
                Text(
                    "Sledenje se je prekinilo. Nadaljuješ lahko od trenutne lokacije ali pot zaključiš z zadnjo shranjeno lokacijo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text("Nadaljuj sledenje")
                }
                OutlinedButton(
                    onClick = onFinishRecovered,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text("Zaključi zdaj")
                }
                if (recoveryState is StartState.Error) {
                    Text(
                        recoveryState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text("Končaj vožnjo")
                }
            }
        }
    }
}

@Composable
private fun GpsHealthRow(
    diagnostics: TrackingDiagnosticsState,
    elapsedRealtimeNow: Long,
) {
    val ageSeconds = diagnostics.lastFixElapsedRealtimeMs?.let {
        ((elapsedRealtimeNow - it).coerceAtLeast(0L)) / 1000.0
    }
    val quality = GpsSignalEvaluator.quality(
        isTracking = diagnostics.isTracking,
        locationAvailable = diagnostics.locationAvailable,
        lastFixAgeSeconds = ageSeconds,
        accuracyMeters = diagnostics.lastAccuracyMeters,
    )
    val accuracy = diagnostics.lastAccuracyMeters?.toInt()
    val (title, detail) = when (quality) {
        GpsSignalQuality.Good -> "GPS dober" to accuracy?.let { "±$it m" }
        GpsSignalQuality.Fair -> "GPS srednji" to accuracy?.let { "±$it m" }
        GpsSignalQuality.Poor -> {
            if (ageSeconds != null && ageSeconds > 15.0) {
                "GPS zamuja" to "zadnja meritev ${ageSeconds.toInt()} s"
            } else {
                "GPS šibek" to accuracy?.let { "±$it m" }
            }
        }
        GpsSignalQuality.Lost -> "GPS signal izgubljen" to ageSeconds?.let { "zadnja meritev ${it.toInt()} s" }
        GpsSignalQuality.Unavailable -> "Lokacija ni na voljo" to "preveri GPS v telefonu"
        GpsSignalQuality.Waiting -> "Čakam na GPS" to null
    }
    val icon = when (quality) {
        GpsSignalQuality.Good, GpsSignalQuality.Fair -> Icons.Outlined.GpsFixed
        GpsSignalQuality.Poor, GpsSignalQuality.Lost, GpsSignalQuality.Waiting -> Icons.Outlined.GpsNotFixed
        GpsSignalQuality.Unavailable -> Icons.Outlined.LocationOff
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(title, fontWeight = FontWeight.SemiBold)
                }
                detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (diagnostics.rejectedSegments > 0) {
                val lastReason = diagnostics.lastRejectionReason?.let(::rejectionReasonLabel)
                Text(
                    buildString {
                        append("Filtrirano ${diagnostics.rejectedSegments} slabih meritev")
                        if (lastReason != null) append(" • nazadnje: $lastReason")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun rejectionReasonLabel(reason: SegmentRejectionReason): String = when (reason) {
    SegmentRejectionReason.InvalidMeasurement -> "neveljavna meritev"
    SegmentRejectionReason.StaleFix -> "stara GPS meritev"
    SegmentRejectionReason.LowAccuracy -> "slaba natančnost"
    SegmentRejectionReason.GpsJitter -> "GPS odmik"
    SegmentRejectionReason.ImpossibleSpeed -> "GPS skok"
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}
