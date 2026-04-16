package com.pacetrack.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.pacetrack.data.model.ActivityType
import com.pacetrack.data.model.Photo
import com.pacetrack.util.DistanceFormatter
import com.pacetrack.util.PaceFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RouteDetailScreen(
    runId: String,
    onNavigateBack: () -> Unit,
    viewModel: RouteDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is RouteDetailUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        is RouteDetailUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }

        is RouteDetailUiState.Success -> {
            RouteDetailContent(
                detail = state.detail,
                onBack = onNavigateBack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteDetailContent(
    detail: RouteDetail,
    onBack: () -> Unit
) {
    val run = detail.run
    val polylinePoints = detail.points.map { LatLng(it.latitude, it.longitude) }

    val cameraPositionState = rememberCameraPositionState {
        if (polylinePoints.isNotEmpty()) {
            position = CameraPosition.fromLatLngZoom(polylinePoints.first(), 15f)
        }
    }

    var selectedPhoto by remember { mutableStateOf<Photo?>(null) }
    val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(run.startTime.toDate())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (run.type == ActivityType.WALK) "Walk Detail" else "Run Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    cameraPositionState = cameraPositionState
                ) {
                    if (polylinePoints.isNotEmpty()) {
                        Polyline(
                            points = polylinePoints,
                            color = MaterialTheme.colorScheme.primary,
                            width = 12f
                        )
                        Marker(
                            state = rememberMarkerState(position = polylinePoints.first()),
                            title = "Start",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                        )
                        Marker(
                            state = rememberMarkerState(position = polylinePoints.last()),
                            title = "Finish"
                        )
                    }

                    detail.photos.forEach { photo ->
                        Marker(
                            state = rememberMarkerState(position = LatLng(photo.latitude, photo.longitude)),
                            title = "Photo",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                            onClick = {
                                selectedPhoto = photo
                                false
                            }
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(16.dp))

                    StatGrid(run = run)
                }
            }

            if (detail.photos.isNotEmpty()) {
                item {
                    Text(
                        text = "Photos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .heightIn(max = 500.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = false
                    ) {
                        items(detail.photos) { photo ->
                            AsyncImage(
                                model = photo.imageUrl,
                                contentDescription = "Route photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedPhoto = photo }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedPhoto?.let { photo ->
        Dialog(onDismissRequest = { selectedPhoto = null }) {
            AsyncImage(
                model = photo.imageUrl,
                contentDescription = "Full screen photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selectedPhoto = null }
            )
        }
    }
}

@Composable
private fun StatGrid(run: com.pacetrack.data.model.Run) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailStatCard(Modifier.weight(1f), "Distance", DistanceFormatter.format(run.distance))
            DetailStatCard(Modifier.weight(1f), "Duration", PaceFormatter.formatElapsed(run.duration))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailStatCard(Modifier.weight(1f), "Avg Pace", PaceFormatter.format(run.avgPace))
            DetailStatCard(Modifier.weight(1f), "Steps", run.stepCount.toString())
        }
    }
}

@Composable
private fun DetailStatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
