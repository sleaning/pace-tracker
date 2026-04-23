package com.pacetrack.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.pacetrack.BuildConfig
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * Route preview used when Google Maps cannot render, usually because the app
 * was built without a Maps API key on the current machine.
 */
@Composable
fun MapFallback(
    points: List<LatLng>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    title: String = "Map unavailable",
    message: String = "Add MAPS_API_KEY to local.properties or your environment to enable Google Maps."
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.secondary

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = 24.dp.toPx()
                val previewPoints = points.toPreviewOffsets(
                    width = size.width,
                    height = size.height,
                    padding = padding
                )

                // Draw a subtle grid so the route still has spatial context.
                val gridColor = lineColor.copy(alpha = 0.08f)
                repeat(4) { index ->
                    val fraction = (index + 1) / 5f
                    val x = size.width * fraction
                    val y = size.height * fraction
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                when {
                    previewPoints.size >= 2 -> {
                        val routePath = Path().apply {
                            moveTo(previewPoints.first().x, previewPoints.first().y)
                            previewPoints.drop(1).forEach { point ->
                                lineTo(point.x, point.y)
                            }
                        }
                        drawPath(
                            path = routePath,
                            color = lineColor,
                            style = Stroke(
                                width = 10.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                        drawCircle(
                            color = startColor,
                            radius = 7.dp.toPx(),
                            center = previewPoints.first()
                        )
                        drawCircle(
                            color = endColor,
                            radius = 7.dp.toPx(),
                            center = previewPoints.last()
                        )
                    }

                    previewPoints.size == 1 -> {
                        drawCircle(
                            color = lineColor,
                            radius = 8.dp.toPx(),
                            center = previewPoints.first()
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (points.isEmpty()) {
                Text(
                    text = "Waiting for route points...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
        }
    }
}

fun isMapsConfigured(): Boolean = BuildConfig.MAPS_API_KEY.isNotBlank()

@Composable
fun rememberMapFallbackVisible(
    mapsConfigured: Boolean,
    mapLoaded: Boolean,
    timeoutMillis: Long = 3_000L
): Boolean {
    var timedOut by remember(mapsConfigured) { mutableStateOf(false) }

    LaunchedEffect(mapsConfigured, mapLoaded, timeoutMillis) {
        timedOut = false
        if (mapsConfigured && !mapLoaded) {
            delay(timeoutMillis)
            if (!mapLoaded) timedOut = true
        }
    }

    return !mapsConfigured || timedOut
}

private fun List<LatLng>.toPreviewOffsets(
    width: Float,
    height: Float,
    padding: Float
): List<Offset> {
    if (isEmpty()) return emptyList()
    if (size == 1) return listOf(Offset(width / 2f, height / 2f))

    val minLat = minOf { it.latitude }
    val maxLat = maxOf { it.latitude }
    val minLng = minOf { it.longitude }
    val maxLng = maxOf { it.longitude }

    val latSpan = max((maxLat - minLat).toFloat(), 0.0001f)
    val lngSpan = max((maxLng - minLng).toFloat(), 0.0001f)
    val availableWidth = max(width - padding * 2f, 1f)
    val availableHeight = max(height - padding * 2f, 1f)
    val scale = min(availableWidth / lngSpan, availableHeight / latSpan)
    val contentWidth = lngSpan * scale
    val contentHeight = latSpan * scale
    val offsetX = (width - contentWidth) / 2f
    val offsetY = (height - contentHeight) / 2f

    return map { point ->
        val x = offsetX + ((point.longitude - minLng).toFloat() * scale)
        val y = height - (offsetY + ((point.latitude - minLat).toFloat() * scale))
        Offset(x, y)
    }
}
