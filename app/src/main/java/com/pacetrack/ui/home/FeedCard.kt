package com.pacetrack.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.LatLng
import com.pacetrack.data.model.ActivityType
import com.pacetrack.data.model.Run
import com.pacetrack.data.model.User
import com.pacetrack.data.model.displayTitle
import com.pacetrack.util.DistanceFormatter
import com.pacetrack.util.PaceFormatter
import com.pacetrack.util.PolylineEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Reusable card used by the home feed to summarize a saved run in a
 * Strava-inspired stacked format with athlete context, route preview, and a
 * clear hierarchy between primary and supporting stats.
 */
@Composable
fun FeedCard(item: HomeFeedItem, onClick: () -> Unit) {
    val run = item.run
    val athlete = item.athlete
    val startDate = remember(run.startTime) { run.startTime.toDate() }
    val timestampLabel = remember(startDate) { formatFeedTimestamp(startDate) }
    val headline = remember(run.title, startDate, run.type) { buildActivityHeadline(run, startDate) }
    val routePoints = remember(run.encodedPolyline) {
        runCatching {
            if (run.encodedPolyline.isBlank()) emptyList() else PolylineEncoder.decode(run.encodedPolyline)
        }.getOrDefault(emptyList())
    }
    val primaryStats = remember(run.distance, run.duration, run.avgPace, run.elevationGain) {
        buildPrimaryStats(run)
    }
    val secondaryStats = remember(
        run.bestPace,
        run.stepCount,
        run.avgCadence,
        run.elevationGain,
        run.photoIds,
        routePoints
    ) {
        buildSecondaryStats(run, hasRoutePreview = routePoints.isNotEmpty())
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, FeedBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AthleteAvatar(athlete = athlete)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = athlete?.displayName?.takeIf { it.isNotBlank() }
                            ?: "Athlete ${run.userId.take(5)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = FeedText
                    )
                    Text(
                        text = buildHeaderMetaLine(athlete, timestampLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = FeedMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ActivityBadge(type = run.type)
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = FeedText
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (run.type == ActivityType.RUN) {
                    "Activity summary with route preview and key run stats."
                } else {
                    "Activity summary with route preview and key walk stats."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = FeedMuted
            )

            Spacer(Modifier.height(16.dp))

            RoutePreviewCard(
                run = run,
                routePoints = routePoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                primaryStats.forEach { stat ->
                    PrimaryStat(
                        label = stat.label,
                        value = stat.value,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (secondaryStats.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    secondaryStats.take(3).forEach { stat ->
                        SecondaryStatChip(
                            label = stat.label,
                            value = stat.value,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Large stat block used for the hero metrics row.
 * These values mirror the emphasis Strava gives to the core workout numbers.
 */
@Composable
private fun PrimaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = FeedSurface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = FeedMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = FeedText,
                lineHeight = 28.sp
            )
        }
    }
}

@Composable
private fun SecondaryStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = FeedSurface.copy(alpha = 0.78f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = FeedText
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = FeedMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AthleteAvatar(athlete: User?) {
    val avatarSize = 48.dp
    val profileUrl = athlete?.profilePhotoUrl?.takeIf { it.isNotBlank() }

    if (profileUrl != null) {
        AsyncImage(
            model = profileUrl,
            contentDescription = athlete.displayName.ifBlank { "Athlete avatar" },
            modifier = Modifier
                .size(avatarSize)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, FeedBorder, RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
        return
    }

    Surface(
        modifier = Modifier.size(avatarSize),
        shape = RoundedCornerShape(16.dp),
        color = FeedSurface
    ) {
        Box(contentAlignment = Alignment.Center) {
            val initials = athlete?.displayName
                ?.split(" ")
                ?.mapNotNull { token -> token.firstOrNull()?.uppercaseChar()?.toString() }
                ?.take(2)
                ?.joinToString("")
                ?.takeIf { it.isNotBlank() }

            if (initials != null) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FeedText
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = FeedMuted,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun ActivityBadge(type: ActivityType) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = FeedOrange.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (type == ActivityType.RUN) {
                    Icons.AutoMirrored.Filled.DirectionsRun
                } else {
                    Icons.AutoMirrored.Filled.DirectionsWalk
                },
                contentDescription = null,
                tint = FeedOrange,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = type.displayName().uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = FeedOrange
            )
        }
    }
}

@Composable
private fun RoutePreviewCard(
    run: Run,
    routePoints: List<LatLng>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = FeedRouteBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White, FeedRouteBackground)
                    )
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = 22.dp.toPx()
                val previewPoints = routePoints.toPreviewOffsets(
                    width = size.width,
                    height = size.height,
                    padding = padding
                )

                val gridColor = FeedRouteGrid.copy(alpha = 0.55f)
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
                        val path = Path().apply {
                            moveTo(previewPoints.first().x, previewPoints.first().y)
                            previewPoints.drop(1).forEach { point ->
                                lineTo(point.x, point.y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = FeedRouteLine,
                            style = Stroke(
                                width = 9.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )

                        drawCircle(
                            color = FeedRouteStart,
                            radius = 7.dp.toPx(),
                            center = previewPoints.first()
                        )
                        drawCircle(
                            color = FeedRouteLine,
                            radius = 7.dp.toPx(),
                            center = previewPoints.last()
                        )
                    }

                    previewPoints.size == 1 -> {
                        drawCircle(
                            color = FeedRouteLine,
                            radius = 8.dp.toPx(),
                            center = previewPoints.first()
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivitySummaryPill(
                    text = buildRoutePillLabel(run, routePoints.isNotEmpty())
                )
                if (run.photoIds.isNotEmpty()) {
                    ActivitySummaryPill(text = "${run.photoIds.size} photo${if (run.photoIds.size == 1) "" else "s"}")
                }
            }

            if (routePoints.isEmpty()) {
                Text(
                    text = "No GPS route preview available for this activity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FeedMuted,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp)
                )
            }
        }
    }
}

@Composable
private fun ActivitySummaryPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.94f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = FeedText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

private data class FeedStat(
    val label: String,
    val value: String
)

private fun buildPrimaryStats(run: Run): List<FeedStat> {
    val thirdStat = if (shouldHighlightElevation(run)) {
        FeedStat(label = "Elev Gain", value = formatElevation(run.elevationGain))
    } else {
        FeedStat(label = "Avg Pace", value = PaceFormatter.format(run.avgPace))
    }

    return listOf(
        FeedStat(label = "Distance", value = DistanceFormatter.format(run.distance)),
        FeedStat(label = "Duration", value = PaceFormatter.formatElapsed(run.duration)),
        thirdStat
    )
}

private fun buildSecondaryStats(run: Run, hasRoutePreview: Boolean): List<FeedStat> {
    val stats = mutableListOf<FeedStat>()

    if (run.bestPace > 0f) {
        stats += FeedStat(label = "Best Pace", value = PaceFormatter.format(run.bestPace))
    }
    if (run.stepCount > 0) {
        stats += FeedStat(label = "Steps", value = run.stepCount.toString())
    }
    if (run.avgCadence > 0f) {
        stats += FeedStat(label = "Cadence", value = "${run.avgCadence.toInt()} spm")
    }
    if (run.elevationGain > 0f && !shouldHighlightElevation(run)) {
        stats += FeedStat(label = "Elevation", value = formatElevation(run.elevationGain))
    }
    if (run.photoIds.isNotEmpty()) {
        stats += FeedStat(label = "Media", value = "${run.photoIds.size} photo${if (run.photoIds.size == 1) "" else "s"}")
    }
    if (hasRoutePreview) {
        stats += FeedStat(label = "Route", value = "GPS mapped")
    }

    return stats
}

private fun buildHeaderMetaLine(athlete: User?, timestampLabel: String): String {
    val username = athlete?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
    return listOfNotNull(username, timestampLabel).joinToString(" • ")
}

private fun buildActivityHeadline(run: Run, startDate: Date): String {
    if (run.title.isNotBlank()) return run.displayTitle()

    val hour = Calendar.getInstance().apply { time = startDate }.get(Calendar.HOUR_OF_DAY)
    val timeOfDay = when (hour) {
        in 4..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..21 -> "Evening"
        else -> "Late Night"
    }
    return "$timeOfDay ${run.type.displayName()}"
}

private fun formatFeedTimestamp(date: Date): String {
    val locale = Locale.getDefault()
    val now = Calendar.getInstance()
    val activityDay = Calendar.getInstance().apply { time = date }
    val timeFormat = SimpleDateFormat("h:mm a", locale)

    return when {
        now.isSameDayAs(activityDay) -> "Today at ${timeFormat.format(date)}"
        now.copyAndShiftBy(days = -1).isSameDayAs(activityDay) ->
            "Yesterday at ${timeFormat.format(date)}"
        else -> SimpleDateFormat("EEE, MMM d 'at' h:mm a", locale).format(date)
    }
}

private fun buildRoutePillLabel(run: Run, hasRoutePreview: Boolean): String {
    return if (hasRoutePreview) {
        "${run.type.displayName()} route preview"
    } else {
        "${run.type.displayName()} summary"
    }
}

private fun ActivityType.displayName(): String = if (this == ActivityType.RUN) "Run" else "Walk"

private fun shouldHighlightElevation(run: Run): Boolean {
    if (run.elevationGain <= 0f || run.distance <= 0f) return false

    val feetPerMile = (run.elevationGain * 3.28084f) / (run.distance / 1609.34f)
    return feetPerMile >= 100f
}

private fun formatElevation(elevationMeters: Float): String = "${elevationMeters.toInt()} m"

private fun Calendar.isSameDayAs(other: Calendar): Boolean {
    return get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
}

private fun Calendar.copyAndShiftBy(days: Int): Calendar {
    return (clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) }
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
    val availableWidth = max(width - (padding * 2f), 1f)
    val availableHeight = max(height - (padding * 2f), 1f)
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

private val FeedOrange = Color(0xFFFC4C02)
private val FeedText = Color(0xFF171717)
private val FeedMuted = Color(0xFF6F6A63)
private val FeedBorder = Color(0xFFE7E1D7)
private val FeedSurface = Color(0xFFF7F4EE)
private val FeedRouteBackground = Color(0xFFF4F0E7)
private val FeedRouteGrid = Color(0xFFD9D2C4)
private val FeedRouteLine = Color(0xFFFC4C02)
private val FeedRouteStart = Color(0xFF2D6A4F)
