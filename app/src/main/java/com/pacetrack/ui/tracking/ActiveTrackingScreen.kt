package com.pacetrack.ui.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.pacetrack.ui.tracking.components.LiveStatsBar

/**
 * ActiveTrackingScreen
 *
 * Shown during an active run. Displays:
 *  - A full-screen Google Map with a live polyline of the route so far.
 *  - The camera follows the user's current position automatically.
 *  - A LiveStatsBar at the bottom showing time, distance, and pace.
 *  - A Stop button that ends the run and navigates to PostRunSummaryScreen.
 *
 * All state comes from TrackingViewModel which reads TrackingService's
 * companion StateFlows — so screen rotation has no effect on the run.
 */
@Composable
fun ActiveTrackingScreen(
    onRunFinished: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val routePoints by viewModel.routePoints.collectAsState()
    val distanceMetres by viewModel.distanceMetres.collectAsState()
    val currentPaceSec by viewModel.currentPaceSec.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val stepCount by viewModel.stepCount.collectAsState()

    var showStopDialog by remember { mutableStateOf(false) }

    // Convert RoutePoints to LatLng for the map
    val latLngs = remember(routePoints) {
        routePoints.map { LatLng(it.latitude, it.longitude) }
    }

    // Camera position — follows the last known GPS point
    val lastPoint = latLngs.lastOrNull() ?: LatLng(0.0, 0.0)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(lastPoint, 16f)
    }

    // Keep camera centred on user as they move
    LaunchedEffect(lastPoint) {
        if (latLngs.isNotEmpty()) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(lastPoint, 16f)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Full-screen map ───────────────────────────────────────────────────
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false
            )
        ) {
            // Draw the route polyline as the user moves
            if (latLngs.size >= 2) {
                Polyline(
                    points = latLngs,
                    color = Color(0xFF2D6A4F),   // Trail Green from MVP plan
                    width = 12f
                )
            }

            // Dot at the starting point
            if (latLngs.isNotEmpty()) {
                Circle(
                    center = latLngs.first(),
                    radius = 6.0,
                    fillColor = Color(0xFF2D6A4F),
                    strokeColor = Color.White,
                    strokeWidth = 3f
                )
            }
        }

        // ── Stats bar + Stop button pinned to the bottom ──────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            LiveStatsBar(
                elapsedMs = elapsedMs,
                distanceMetres = distanceMetres,
                currentPaceSec = currentPaceSec,
                stepCount = stepCount
            )

            Button(
                onClick = { showStopDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Stop run",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stop Run",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // ── Stop confirmation dialog ──────────────────────────────────────────────
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop your run?") },
            text = { Text("Your run will be saved and you can review your stats.") },
            confirmButton = {
                Button(
                    onClick = {
                        showStopDialog = false
                        viewModel.stopRun()
                        onRunFinished()
                    }
                ) {
                    Text("Stop & Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStopDialog = false }) {
                    Text("Keep Going")
                }
            }
        )
    }
}