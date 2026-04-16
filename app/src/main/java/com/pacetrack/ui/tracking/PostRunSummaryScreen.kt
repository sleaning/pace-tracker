package com.pacetrack.ui.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.pacetrack.data.model.ActivityType
import com.pacetrack.util.DistanceFormatter
import com.pacetrack.util.PaceFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostRunSummaryScreen(
    activityType: ActivityType,
    onSaved: () -> Unit,
    onDiscarded: () -> Unit,
    viewModel: PostRunSummaryViewModel = hiltViewModel(),
    trackingViewModel: TrackingViewModel = hiltViewModel()
) {
    val saveState by viewModel.saveState.collectAsState()
    val snapshot = trackingViewModel.sessionSnapshot()

    // Navigation Logic
    LaunchedEffect(saveState) {
        if (saveState is SaveState.Saved) {
            trackingViewModel.resetSession()
            onSaved()
        }
    }

    // Map Setup
    val polylinePoints = snapshot.routePoints.map { LatLng(it.latitude, it.longitude) }
    val cameraPositionState = rememberCameraPositionState {
        if (polylinePoints.isNotEmpty()) {
            position = CameraPosition.fromLatLngZoom(polylinePoints.first(), 14f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run Complete", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header message
            Text(
                text = if (activityType == ActivityType.RUN) "Great run! 🏃" else "Great walk! 🚶",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            // Route Map
            if (polylinePoints.isNotEmpty()) {
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        scrollGesturesEnabled = false,
                        zoomGesturesEnabled = false
                    )
                ) {
                    Polyline(
                        points = polylinePoints,
                        color = MaterialTheme.colorScheme.primary,
                        width = 10f
                    )
                    Marker(
                        state = rememberMarkerState(position = polylinePoints.first()),
                        title = "Start"
                    )
                    Marker(
                        state = rememberMarkerState(position = polylinePoints.last()),
                        title = "Finish"
                    )
                }
            }

            // Stats Grid
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatRow(
                    label1 = "Distance", value1 = DistanceFormatter.format(snapshot.distanceMetres),
                    label2 = "Duration", value2 = PaceFormatter.formatElapsed(snapshot.durationMs)
                )
                StatRow(
                    label1 = "Avg Pace", value1 = PaceFormatter.format(snapshot.avgPaceSecPerKm),
                    label2 = "Steps", value2 = snapshot.stepCount.toString()
                )
                StatRow(
                    label1 = "Best Pace", value1 = PaceFormatter.format(snapshot.bestPaceSecPerKm),
                    label2 = "Cadence", value2 = "${snapshot.avgCadence.toInt()} spm"
                )
            }

            Spacer(Modifier.weight(1f))

            // Actions
            val isSaving = saveState is SaveState.Saving

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.saveRun(activityType, snapshot) },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Run", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        trackingViewModel.resetSession()
                        onDiscarded()
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Discard", fontSize = 16.sp)
                }
            }

            if (saveState is SaveState.Error) {
                Text(
                    text = (saveState as SaveState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun StatRow(label1: String, value1: String, label2: String, value2: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(modifier = Modifier.weight(1f), label = label1, value = value1)
        StatCard(modifier = Modifier.weight(1f), label = label2, value = value2)
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
