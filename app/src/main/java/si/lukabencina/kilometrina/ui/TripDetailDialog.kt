package si.lukabencina.kilometrina.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.RenderOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox
import si.lukabencina.kilometrina.data.LocationPointEntity
import si.lukabencina.kilometrina.data.TripEntity
import java.util.Locale
import kotlin.math.ceil
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
                        RouteMap(routeState.points)
                        val averageAccuracy = routeState.points.map { it.accuracyMeters }.average()
                        Text(
                            "${routeState.points.size} GPS točk • povprečna natančnost ±${averageAccuracy.toInt()} m",
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
        Text("Relacija", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun RouteMap(points: List<LocationPointEntity>) {
    val sampled = remember(points) { sanitizeAndSampleRoute(points) }
    if (sampled.size < 2) {
        Text("GPS trasa nima dovolj veljavnih točk.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val routeGeoJson = remember(sampled) { lineGeoJson(sampled) }
    val startGeoJson = remember(sampled) { pointGeoJson(sampled.first()) }
    val endGeoJson = remember(sampled) { pointGeoJson(sampled.last()) }
    val bounds = remember(sampled) { routeBounds(sampled) }
    val camera = rememberCameraState()
    val scope = rememberCoroutineScope()
    var mapReady by remember(sampled) { mutableStateOf(false) }
    var mapFailed by remember(sampled) { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val routeColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.error
    val markerStroke = MaterialTheme.colorScheme.surface
    val styleUri = if (isSystemInDarkTheme()) {
        "https://tiles.openfreemap.org/styles/dark"
    } else {
        "https://tiles.openfreemap.org/styles/liberty"
    }

    suspend fun fitRoute() {
        camera.awaitViewport()
        camera.jumpTo(bounds, padding = PaddingValues(28.dp))
    }

    LaunchedEffect(sampled, mapReady) {
        if (mapReady) fitRoute()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            if (mapFailed) {
                RouteCanvasFallback(sampled, Modifier.fillMaxSize())
            } else {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    baseStyle = BaseStyle.Uri(styleUri),
                    cameraState = camera,
                    options = MapOptions(
                        renderOptions = RenderOptions(
                            renderMode = RenderOptions.RenderMode.TextureView,
                            maximumFps = 60,
                        ),
                    ),
                    onMapLoadFinished = {
                        mapFailed = false
                        mapReady = true
                    },
                    onMapLoadFailed = {
                        mapReady = false
                        mapFailed = true
                    },
                ) {
                    val routeSource = rememberGeoJsonSource(GeoJsonData.JsonString(routeGeoJson))
                    val startSource = rememberGeoJsonSource(GeoJsonData.JsonString(startGeoJson))
                    val endSource = rememberGeoJsonSource(GeoJsonData.JsonString(endGeoJson))

                    LineLayer(
                        id = "trip-route-line",
                        source = routeSource,
                        color = const(routeColor),
                        width = const(5.dp),
                    )
                    CircleLayer(
                        id = "trip-route-start",
                        source = startSource,
                        radius = const(7.dp),
                        color = const(startColor),
                        strokeColor = const(markerStroke),
                        strokeWidth = const(2.dp),
                    )
                    CircleLayer(
                        id = "trip-route-end",
                        source = endSource,
                        radius = const(7.dp),
                        color = const(endColor),
                        strokeColor = const(markerStroke),
                        strokeWidth = const(2.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (mapFailed) "Zemljevid ni na voljo – prikazana je lokalna GPS trasa." else "Povleci ali povečaj z dvema prstoma.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!mapFailed) {
                IconButton(
                    onClick = {
                        scope.launch { fitRoute() }
                    },
                ) {
                    Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Prikaži celotno traso")
                }
            }
        }
    }
}

private fun sanitizeAndSampleRoute(points: List<LocationPointEntity>): List<LocationPointEntity> {
    val valid = points
        .asSequence()
        .filter { it.lat.isFinite() && it.lon.isFinite() }
        .filter { it.lat in -85.05112878..85.05112878 && it.lon in -180.0..180.0 }
        .sortedBy { it.timestamp }
        .toList()
    if (valid.size <= 2_000) return valid

    val step = ceil((valid.size - 1) / 1_999.0).toInt().coerceAtLeast(1)
    return valid.filterIndexed { index, _ -> index % step == 0 || index == valid.lastIndex }
}

private fun lineGeoJson(points: List<LocationPointEntity>): String {
    val coordinates = points.joinToString(",") { "[${it.lon},${it.lat}]" }
    return """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
}

private fun pointGeoJson(point: LocationPointEntity): String =
    """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${point.lon},${point.lat}]}}"""

private fun routeBounds(points: List<LocationPointEntity>): BoundingBox {
    val west = points.minOf { it.lon }
    val east = points.maxOf { it.lon }
    val south = points.minOf { it.lat }
    val north = points.maxOf { it.lat }
    val lonPadding = ((east - west) * 0.04).coerceAtLeast(0.0005)
    val latPadding = ((north - south) * 0.04).coerceAtLeast(0.0005)
    return BoundingBox(
        west = (west - lonPadding).coerceAtLeast(-180.0),
        south = (south - latPadding).coerceAtLeast(-85.05112878),
        east = (east + lonPadding).coerceAtMost(180.0),
        north = (north + latPadding).coerceAtMost(85.05112878),
    )
}

@Composable
private fun RouteCanvasFallback(points: List<LocationPointEntity>, modifier: Modifier = Modifier) {
    val routeColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        fun mercatorX(lon: Double): Double = Math.toRadians(lon)
        fun mercatorY(lat: Double): Double {
            val latRadians = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
            return ln(tan(Math.PI / 4.0 + latRadians / 2.0))
        }

        val xs = points.map { mercatorX(it.lon) }
        val ys = points.map { mercatorY(it.lat) }
        val minX = xs.minOrNull() ?: return@Canvas
        val maxX = xs.maxOrNull() ?: return@Canvas
        val minY = ys.minOrNull() ?: return@Canvas
        val maxY = ys.maxOrNull() ?: return@Canvas
        val dx = (maxX - minX).coerceAtLeast(1e-9)
        val dy = (maxY - minY).coerceAtLeast(1e-9)
        val margin = 28f
        val usableWidth = (size.width - margin * 2f).coerceAtLeast(1f)
        val usableHeight = (size.height - margin * 2f).coerceAtLeast(1f)
        val fitScale = min(usableWidth / dx.toFloat(), usableHeight / dy.toFloat())
        val routeWidth = dx.toFloat() * fitScale
        val routeHeight = dy.toFloat() * fitScale
        val originX = (size.width - routeWidth) / 2f
        val originY = (size.height - routeHeight) / 2f

        fun projected(index: Int): Offset = Offset(
            x = originX + (xs[index] - minX).toFloat() * fitScale,
            y = originY + (maxY - ys[index]).toFloat() * fitScale,
        )

        clipRect {
            val path = Path()
            val first = projected(0)
            path.moveTo(first.x, first.y)
            for (i in 1 until points.size) {
                val p = projected(i)
                path.lineTo(p.x, p.y)
            }
            drawPath(path, routeColor, style = Stroke(width = 5f))
            drawCircle(startColor, radius = 8f, center = first)
            drawCircle(endColor, radius = 8f, center = projected(points.lastIndex))
        }
    }
}
