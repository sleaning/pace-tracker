package com.pacetrack.ui.tracking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pacetrack.util.DistanceFormatter
import com.pacetrack.util.PaceFormatter

/**
 * LiveStatsBar
 *
 * The horizontal bar shown at the bottom of ActiveTrackingScreen displaying
 * live run stats: elapsed time, distance, and current pace.
 *
 * Phase 4 will add a Steps column here — the layout is already built to
 * accommodate a 4th StatColumn with no restructuring needed.
 */
@Composable
fun LiveStatsBar(
    elapsedMs: Long,
    distanceMetres: Float,
    currentPaceSec: Float,
    stepCount: Int = 0,       // Phase 4 — wired now, shown when > 0
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatColumn(
            label = "TIME",
            value = PaceFormatter.formatElapsed(elapsedMs)
        )

        StatDivider()

        StatColumn(
            label = "DISTANCE",
            value = DistanceFormatter.format(distanceMetres)
        )

        StatDivider()

        StatColumn(
            label = "PACE",
            value = PaceFormatter.format(currentPaceSec)
        )

        // Phase 4: steps column appears once sensor data arrives
        if (stepCount > 0) {
            StatDivider()
            StatColumn(
                label = "STEPS",
                value = stepCount.toString()
            )
        }
    }
}

/**
 * One labeled metric cell inside the live stats bar.
 * Separating it from the row keeps the tracking screen focused on data flow
 * while this helper handles typography for each live metric.
 */
@Composable
private fun StatColumn(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Thin vertical separator between live stat groups.
 * It gives the bottom bar more structure without adding another container.
 */
@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
