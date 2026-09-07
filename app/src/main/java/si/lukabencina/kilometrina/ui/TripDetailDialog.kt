package si.lukabencina.kilometrina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import si.lukabencina.kilometrina.data.LocationPointEntity
import si.lukabencina.kilometrina.data.TripEntity
import java.util.Locale
import kotlin.math.cos

@Composable
fun TripDetailDialog(
    trip: TripEntity,
    routeState: TripRouteUiState,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trip.purpose, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatDate(trip.startTime)} • ${formatTime(trip.startTime)}–${trip.endTime?.let(::formatTime).orEmpty()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Zapri podrobnosti")
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailLine("Relacija", "${shortLocation(trip.startAddress)} → ${shortLocation(trip.endAddress)}")
                        DetailLine("Razdalja", formatKm(trip.distanceMeters))
                        DetailLine("Postavka", String.format(Locale.forLanguageTag("sl-SI"), "%.2f €/km", trip.ratePerKm))
                        DetailLine("Kilometrina", formatMoney(tripCompensation(trip)))
                        if (trip.parkingCents > 0) DetailLine("Parkirnina", formatMoney(trip.parkingCents / 100.0))
                        if (trip.tollsCents > 0) DetailLine("Cestnina", formatMoney(trip.tollsCents / 100.0))
                        DetailLine("Skupaj", formatMoney(tripTotalCost(trip)), emphasized = true)
                        val vehicle = listOf(trip.vehicleName, trip.registrationPlate).filter { it.isNotBlank() }.joinToString(" • ")
                        if (vehicle.isNotBlank()) DetailLine("Vozilo", vehicle)
                    }
                }

                HorizontalDivider()
                Text("GPS trasa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                when {
                    routeState.loading || routeState.tripId != trip.id -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(10.dp))
                            Text("Nalagam GPS točke …", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    routeState.points.size < 2 -> {
                        Text(
                            "Za to vožnjo ni shranjene GPS trase. To je običajno pri ročno dodanih vožnjah.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        RoutePreview(routeState.points)
                        val averageAccuracy = routeState.points.map { it.accuracyMeters }.average()
                        Text(
                            "${routeState.points.size} GPS točk • povprečna natančnost ±${averageAccuracy.toInt()} m • povleci ali povečaj z dvema prstoma",
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
private fun DetailLine(label: String, value: String, emphasized: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun RoutePreview(points: List<LocationPointEntity>) {
    val sampled = remember(points) {
        val valid = points.filter { it.lat.isFinite() && it.lon.isFinite() }
        if (valid.size <= 2_000) valid else {
            val step = (valid.size / 2_000).coerceAtLeast(1)
            valid.filterIndexed { index, _ -> index % step == 0 || index == valid.lastIndex }
        }
    }
    var zoom by remember(points) { mutableFloatStateOf(1f) }
    var pan by remember(points) { mutableStateOf(Offset.Zero) }
    val routeColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    val background = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .background(background, RoundedCornerShape(20.dp))
                .pointerInput(sampled) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 8f)
                        pan = Offset(
                            x = (pan.x + panChange.x).coerceIn(-size.width * 2f, size.width * 2f),
                            y = (pan.y + panChange.y).coerceIn(-size.height * 2f, size.height * 2f),
                        )
                    }
                },
        ) {
            for (i in 1..4) {
                val x = size.width * i / 5f
                val y = size.height * i / 5f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            if (sampled.size < 2) return@Canvas

            val midLatRadians = sampled.map { it.lat }.average() * Math.PI / 180.0
            val lonScale = cos(midLatRadians).coerceAtLeast(0.1)
            val xs = sampled.map { it.lon * lonScale }
            val ys = sampled.map { it.lat }
            val minX = xs.minOrNull() ?: return@Canvas
            val maxX = xs.maxOrNull() ?: return@Canvas
            val minY = ys.minOrNull() ?: return@Canvas
            val maxY = ys.maxOrNull() ?: return@Canvas
            val dx = (maxX - minX).coerceAtLeast(0.000001)
            val dy = (maxY - minY).coerceAtLeast(0.000001)
            val margin = 24f
            val center = Offset(size.width / 2f, size.height / 2f)

            fun projected(index: Int): Offset {
                val baseX = margin + ((xs[index] - minX) / dx).toFloat() * (size.width - 2 * margin)
                val baseY = size.height - margin - ((ys[index] - minY) / dy).toFloat() * (size.height - 2 * margin)
                return Offset(
                    x = center.x + (baseX - center.x) * zoom + pan.x,
                    y = center.y + (baseY - center.y) * zoom + pan.y,
                )
            }

            val path = Path()
            val first = projected(0)
            path.moveTo(first.x, first.y)
            for (i in 1 until sampled.size) {
                val p = projected(i)
                path.lineTo(p.x, p.y)
            }
            drawPath(path, routeColor, style = Stroke(width = 5f))
            drawCircle(startColor, radius = 8f, center = first)
            drawCircle(endColor, radius = 8f, center = projected(sampled.lastIndex))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { zoom = (zoom / 1.5f).coerceAtLeast(1f) }) {
                Icon(Icons.Outlined.ZoomOut, contentDescription = "Pomanjšaj traso")
            }
            IconButton(onClick = { zoom = 1f; pan = Offset.Zero }) {
                Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Prikaži celotno traso")
            }
            IconButton(onClick = { zoom = (zoom * 1.5f).coerceAtMost(8f) }) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = "Povečaj traso")
            }
        }
    }
}
