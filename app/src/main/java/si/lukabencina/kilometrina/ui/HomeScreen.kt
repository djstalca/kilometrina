package si.lukabencina.kilometrina.ui

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lukabencina.kilometrina.data.TripEntity
import java.util.Locale

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
    var purpose by rememberSaveable { mutableStateOf("") }
    var lastAppliedDefaultPurpose by rememberSaveable { mutableStateOf("") }
    var startState by remember { mutableStateOf<StartState>(StartState.Idle) }
    var pendingAction by remember { mutableStateOf<PendingLocationAction?>(null) }
    var showStopDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.settings.defaultPurpose) {
        if (purpose.isBlank() || purpose == lastAppliedDefaultPurpose) {
            purpose = uiState.settings.defaultPurpose
        }
        lastAppliedDefaultPurpose = uiState.settings.defaultPurpose
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
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

    fun requestLocation(action: PendingLocationAction) {
        pendingAction = action
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(permissions)
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
                    ratePerKm = uiState.settings.ratePerKm,
                    startState = startState,
                    onStart = { requestLocation(PendingLocationAction.Start) },
                )
            } else {
                ActiveTripCard(
                    trip = requireNotNull(uiState.activeTrip),
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
    recoveryRequired: Boolean,
    recoveryState: StartState,
    onResume: () -> Unit,
    onFinishRecovered: () -> Unit,
    onStop: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(trip.id, recoveryRequired) {
        while (!recoveryRequired) {
            now = System.currentTimeMillis()
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
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RecentTripCard(trip: TripEntity, onOpenTrips: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        onClick = onOpenTrips,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Zadnja vožnja", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Outlined.ArrowForward, contentDescription = "Odpri vožnje")
            }
            Text("${shortLocation(trip.startAddress)} → ${shortLocation(trip.endAddress)}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDate(trip.startTime), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${formatKm(trip.distanceMeters)} • ${formatMoney(tripTotalCost(trip))}", fontWeight = FontWeight.Medium)
            }
        }
    }
}
