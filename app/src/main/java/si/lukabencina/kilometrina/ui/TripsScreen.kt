package si.lukabencina.kilometrina.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import si.lukabencina.kilometrina.data.TripEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("sl-SI"))

@Composable
fun TripsScreen(
    trips: List<TripEntity>,
    onDeleteTrip: (Long) -> Unit,
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
    val monthAmount = monthTrips.sumOf(::tripCompensation)
    var deleteCandidate by remember { mutableStateOf<TripEntity?>(null) }

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
                IconButton(
                    onClick = { exportLauncher.launch("kilometrina-${selectedMonth}.csv") },
                    enabled = monthTrips.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "Izvozi izbrani mesec")
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
                        SummaryMetric("Kilometri", String.format(Locale.forLanguageTag("sl-SI"), "%.1f km", monthKm), Modifier.weight(1f))
                        SummaryMetric("Kilometrina", formatMoney(monthAmount), Modifier.weight(1f))
                    }
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

        if (completedTrips.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        "Ko zaključiš prvo vožnjo, se bo prikazala tukaj.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(completedTrips, key = { it.id }) { trip ->
                TripRow(trip = trip, onDelete = { deleteCandidate = trip })
            }
        }
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
private fun TripRow(trip: TripEntity, onDelete: () -> Unit) {
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
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Izbriši vožnjo")
                }
            }
            Text("${shortLocation(trip.startAddress)} → ${shortLocation(trip.endAddress)}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatKm(trip.distanceMeters), fontWeight = FontWeight.Medium)
                Text(formatMoney(tripCompensation(trip)), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
