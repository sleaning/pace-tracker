package com.pacetrack.ui.tracking

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.pacetrack.data.model.ActivityType
import com.pacetrack.util.DistanceFormatter
import com.pacetrack.util.PaceFormatter
import kotlinx.coroutines.launch
import java.io.File

/**
 * Post-run review page shown immediately after tracking stops.
 * It freezes the latest session stats into a snapshot, lets the user attach
 * photos, and then either saves the completed run or discards it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostRunSummaryScreen(
    activityType: ActivityType,
    onSaved: () -> Unit,
    onDiscarded: () -> Unit,
    viewModel: PostRunSummaryViewModel = hiltViewModel(),
    trackingViewModel: TrackingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val saveState by viewModel.saveState.collectAsState()
    val pendingPhotos by viewModel.pendingPhotos.collectAsState()
    val snapshot = remember { trackingViewModel.sessionSnapshot() }

    // The snapshot is captured once when this screen opens so distance, pace,
    // and route data stay stable even if other state changes during saving.

    // Navigation happens only after the save state reaches Saved, which means
    // the run document and any uploaded photos have both completed.
    LaunchedEffect(saveState) {
        if (saveState is SaveState.Saved) {
            trackingViewModel.resetSession()
            onSaved()
        }
    }

    // ── Photo pickers ─────────────────────────────────────────────────────────
    // Gallery picker — returns a content:// Uri directly
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addPhoto(it) }
    }

    // Camera needs us to provide a destination Uri up front
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            pendingCameraUri?.let { viewModel.addPhoto(it) }
        }
        pendingCameraUri = null
    }

    var showPhotoSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Map state
    val polylinePoints = snapshot.routePoints.map { LatLng(it.latitude, it.longitude) }
    val cameraPositionState = rememberCameraPositionState {
        if (polylinePoints.isNotEmpty()) {
            position = CameraPosition.fromLatLngZoom(polylinePoints.first(), 14f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Run Complete", fontWeight = FontWeight.Bold) })
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
            Text(
                text = if (activityType == ActivityType.RUN) "Great run! 🏃" else "Great walk! 🚶",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            // ── Map ───────────────────────────────────────────────────────────
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

            // ── Stats grid ────────────────────────────────────────────────────
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

            // ── Photos section ────────────────────────────────────────────────
            PhotoSection(
                photos = pendingPhotos,
                onAddClick = { showPhotoSheet = true },
                onRemove = { viewModel.removePhoto(it) },
                enabled = saveState !is SaveState.Saving
            )

            // ── Save / Discard ────────────────────────────────────────────────
            val isSaving = saveState is SaveState.Saving
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.saveRun(context, activityType, snapshot) },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (pendingPhotos.isNotEmpty()) "Uploading..." else "Saving...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Run", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        // Discard intentionally clears the session without
                        // writing anything so the next run starts clean.
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

    // ── Bottom sheet: Camera vs Gallery ───────────────────────────────────────
    if (showPhotoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Add a photo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                PhotoSourceButton(
                    icon = Icons.Default.PhotoCamera,
                    label = "Take photo",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showPhotoSheet = false
                            val uri = createTempImageUri(context)
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    }
                )

                PhotoSourceButton(
                    icon = Icons.Default.PhotoLibrary,
                    label = "Choose from gallery",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showPhotoSheet = false
                            galleryLauncher.launch("image/*")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Preview strip for photos the user has added before saving.
 * The section keeps image selection local to the summary page so uploads only
 * happen if the user actually confirms they want to save the run.
 */
@Composable
private fun PhotoSection(
    photos: List<Uri>,
    onAddClick: () -> Unit,
    onRemove: (Uri) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Photos (${photos.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onAddClick, enabled = enabled) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Photo")
            }
        }

        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos) { uri ->
                    Box(modifier = Modifier.size(90.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Added photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                        // Remove (X) button
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable(enabled = enabled) { onRemove(uri) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove photo",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom-sheet action row for choosing a photo source.
 * It keeps the sheet body small and makes camera and gallery actions share
 * the same layout and typography.
 */
@Composable
private fun PhotoSourceButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 16.sp)
    }
}

/**
 * Two-column stat row used by the summary page.
 * The helper keeps the save screen readable by moving repeated layout code
 * away from the higher-level summary flow.
 */
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

/**
 * Individual summary metric card for the post-run review page.
 * Each card emphasizes one saved value while sharing consistent styling with
 * the rest of the stats grid.
 */
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

/**
 * Creates a temp file in the app's external cache and returns a FileProvider
 * Uri suitable for passing to TakePicture. The camera app writes directly
 * to this Uri.
 */
private fun createTempImageUri(context: Context): Uri {
    val timestamp = System.currentTimeMillis()
    val imageFile = File.createTempFile(
        "run_photo_$timestamp",
        ".jpg",
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    )
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}
