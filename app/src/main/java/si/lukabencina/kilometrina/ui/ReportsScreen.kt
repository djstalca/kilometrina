package si.lukabencina.kilometrina.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import si.lukabencina.kilometrina.data.AppSettings
import si.lukabencina.kilometrina.data.TripEntity

private val reportLocale = Locale.forLanguageTag("sl-SI")
private val reportMonthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", reportLocale)

@Composable
fun ReportsScreen(
    trips: List<TripEntity>,
    settings: AppSettings,
) {
    val context = LocalContext.current
    var selectedMonthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedStatsYear by rememberSaveable { mutableIntStateOf(YearMonth.now().year) }
    val selectedMonth = remember(selectedMonthValue) { YearMonth.parse(selectedMonthValue) }
    val completedTrips = remember(trips) { trips.filter { it.endTime != null } }
    val monthTrips = remember(completedTrips, selectedMonth) {
        completedTrips.filter {
            val date = Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
            YearMonth.from(date) == selectedMonth
        }
    }
    val summary = remember(monthTrips) { ReportCalculator.summarize(monthTrips) }
    val yearStats = remember(completedTrips, selectedStatsYear) {
        StatisticsCalculator.calculate(completedTrips, selectedStatsYear)
    }
    val monthVehicles = remember(monthTrips) {
        monthTrips
            .map { listOf(it.vehicleName, it.registrationPlate).filter(String::isNotBlank).joinToString(" • ") }
            .filter(String::isNotBlank)
            .distinct()
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write("\uFEFF")
                writer.write(CsvExporter.build(monthTrips, selectedMonth, settings))
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                PdfReportExporter.write(output, monthTrips, selectedMonth, settings)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Poročila in statistika", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Text("Pregled leta in mesečni obračuni za oddajo", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            YearStatisticsCard(
                statistics = yearStats,
                onPreviousYear = { selectedStatsYear -= 1 },
                onNextYear = { selectedStatsYear += 1 },
                nextEnabled = selectedStatsYear < YearMonth.now().year,
            )
        }

        if (yearStats.topRoutes.isNotEmpty()) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Najpogostejše relacije", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        yearStats.topRoutes.forEachIndexed { index, route ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${index + 1}. ${route.route}", fontWeight = FontWeight.Medium)
                                    Text(
                                        "${route.tripCount} voženj • ${String.format(reportLocale, "%.1f km", route.distanceKm)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(formatMoney(route.totalAmount), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        if (yearStats.vehicles.isNotEmpty()) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Po vozilih", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        yearStats.vehicles.forEach { vehicle ->
                            ReportProfileLine(
                                vehicle.vehicle,
                                "${String.format(reportLocale, "%.1f km", vehicle.distanceKm)} • ${formatMoney(vehicle.totalAmount)}",
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Mesečni obračun", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { selectedMonthValue = selectedMonth.minusMonths(1).toString() }) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Prejšnji mesec")
                        }
                        Text(
                            selectedMonth.atDay(1).format(reportMonthFormatter).replaceFirstChar { it.uppercase() },
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
                        ReportMetric("Vožnje", summary.tripCount.toString(), Modifier.weight(1f))
                        ReportMetric("Kilometri", String.format(reportLocale, "%.1f km", summary.distanceKm), Modifier.weight(1f))
                        ReportMetric("Kilometrina", formatMoney(summary.mileageAmount), Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    ReportCostLine("Parkirnine", summary.parkingAmount)
                    ReportCostLine("Cestnine", summary.tollsAmount)
                    ReportCostLine("Skupaj povračilo", summary.totalAmount, emphasized = true)
                }
            }
        }

        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Podatki na poročilu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ReportProfileLine("Voznik", settings.driverName)
                    ReportProfileLine("Podjetje", settings.companyName)
                    ReportProfileLine("Vozila", monthVehicles.joinToString(", "))
                    if (settings.driverName.isBlank()) {
                        Text(
                            "V Nastavitvah dopolni ime voznika.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (monthTrips.isNotEmpty() && monthVehicles.isEmpty()) {
                        Text(
                            "Nekatere stare vožnje nimajo zapisanega vozila. Po želji jih lahko dopolniš z urejanjem vožnje.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { pdfLauncher.launch("kilometrina-${selectedMonth}.pdf") },
                    enabled = monthTrips.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Shrani PDF poročilo")
                }
                OutlinedButton(
                    onClick = { csvLauncher.launch("kilometrina-${selectedMonth}.csv") },
                    enabled = monthTrips.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.TableChart, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Shrani CSV za Excel")
                }
                if (monthTrips.isEmpty()) {
                    Text(
                        "V izbranem mesecu še ni zaključenih voženj.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun YearStatisticsCard(
    statistics: YearStatistics,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    nextEnabled: Boolean,
) {
    val maxKm = statistics.months.maxOfOrNull { it.distanceKm }?.coerceAtLeast(1.0) ?: 1.0
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousYear) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Prejšnje leto")
                }
                Text(statistics.year.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onNextYear, enabled = nextEnabled) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Naslednje leto")
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                ReportMetric("Vožnje", statistics.tripCount.toString(), Modifier.weight(1f))
                ReportMetric("Kilometri", String.format(reportLocale, "%.0f km", statistics.distanceKm), Modifier.weight(1f))
                ReportMetric("Skupaj", formatMoney(statistics.totalAmount), Modifier.weight(1f))
            }

            HorizontalDivider()
            Text("Kilometri po mesecih", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                statistics.months.forEach { month ->
                    val ratio = (month.distanceKm / maxKm).coerceIn(0.0, 1.0)
                    val barHeight = if (month.distanceKm <= 0.0) 3.dp else (12 + 72 * ratio).dp
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            Modifier
                                .width(12.dp)
                                .height(barHeight)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            java.time.Month.of(month.month)
                                .getDisplayName(TextStyle.NARROW, reportLocale)
                                .uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            ReportCostLine("Kilometrina", statistics.mileageAmount)
            ReportCostLine("Dodatni stroški", statistics.additionalCosts)
            ReportCostLine("Skupaj leto", statistics.totalAmount, emphasized = true)
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReportCostLine(label: String, value: Double, emphasized: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(formatMoney(value), fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun ReportProfileLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, fontWeight = FontWeight.Medium)
    }
}
