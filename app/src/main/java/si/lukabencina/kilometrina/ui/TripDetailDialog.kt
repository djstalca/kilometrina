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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import si.lukabencina.kilometrina.data.LocationPointEntity
import si.lukabencina.kilometrina.data.TripEntity
import java.util.Locale
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tan

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
                        RouteDetailLine("${shortLocation(trip.startAddress)} → ${shortLocation(trip.endAddress)}")
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
private fun RouteDetailLine(value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Relacija",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String, emphasized: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.width(96.dp).padding(end = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun RoutePreview(points: List<LocationPointEntity>) {
    val sampled = remember(points) {
        val valid = points
            .asSequence()
            .filter { it.lat.isFinite() && it.lon.isFinite() }
            .filter { it.lat in -85.05112878..85.05112878 && it.lon in -180.0..180.0 }
            .sortedBy { it.timestamp }
            .toList()
        if (valid.size <= 2_000) valid else {
            val step = ((valid.size - 1) / 1_999.0).toInt().coerceAtLeast(1)
            valid.filterIndexed { index, _ -> index % step == 0 || index == valid.lastIndex }
        }
    }
    var zoom by remember(points) { mutableFloatStateOf(1f) }
    var pan by remember(points) { mutableStateOf(Offset.Zero) }
    val routeColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.error
    val background = MaterialTheme.colorScheme.surfaceContainerHighest
    val previewShape = RoundedCornerShape(20.dp)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(previewShape)
                .background(background)
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
            if (sampled.size < 2) return@Canvas

            fun mercatorX(lon: Double): Double = Math.toRadians(lon)
            fun mercatorY(lat: Double): Double {
                val latRadians = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
                return ln(tan(Math.PI / 4.0 + latRadians / 2.0))
            }

            val xs = sampled.map { mercatorX(it.lon) }
            val ys = sampled.map { mercatorY(it.lat) }
            val minX = xs.minOrNull() ?: return@Canvas
            val maxX = xs.maxOrNull() ?: return@Canvas
            val minY = ys.minOrNull() ?: return@Canvas
            val maxY = ys.maxOrNull() ?: return@Canvas
            val dx = (maxX - minX).coerceAtLeast(1e-9)
            val dy = (maxY - minY).coerceAtLeast(1e-9)
            val margin = 28f
            val usableWidth = (size.width - margin * 2f).coerceAtLeast(1f)
            val usableHeight = (size.height - margin * 2f).coerceAtLeast(1f)

            // One shared scale for X and Y keeps the geographic shape intact.
            val fitScale = min(usableWidth / dx.toFloat(), usableHeight / dy.toFloat())
            val routeWidth = dx.toFloat() * fitScale
            val routeHeight = dy.toFloat() * fitScale
            val originX = (size.width - routeWidth) / 2f
            val originY = (size.height - routeHeight) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            fun projected(index: Int): Offset {
                val baseX = originX + (xs[index] - minX).toFloat() * fitScale
                val baseY = originY + (maxY - ys[index]).toFloat() * fitScale
                return Offset(
                    x = center.x + (baseX - center.x) * zoom + pan.x,
                    y = center.y + (baseY - center.y) * zoom + pan.y,
                )
            }

            clipRect {
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
