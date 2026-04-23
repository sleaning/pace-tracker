package com.pacetrack.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.pacetrack.data.model.displayTitle
import com.pacetrack.ui.map.MapFallback
import com.pacetrack.ui.map.isMapsConfigured
import com.pacetrack.util.DistanceFormatter
import com.pacetrack.util.PaceFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Detailed page for one saved run or walk.
 * It combines the decoded route map, summary metrics, and attached photos so
 * the user can review exactly what happened during a completed activity.
 */
@Composable
fun RouteDetailScreen(
    runId: String,
    onNavigateBack: () -> Unit,
    onRunUpdated: () -> Unit,
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
                onBack = onNavigateBack,
                onRunUpdated = onRunUpdated,
                viewModel = viewModel
            )
        }
    }
}

/**
 * Success-state content for route detail once data has loaded.
 * The screen renders a static reconstruction of the run using saved points
 * and photos rather than depending on any live tracking session state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteDetailContent(
    detail: RouteDetail,
    onBack: () -> Unit,
    onRunUpdated: () -> Unit,
    viewModel: RouteDetailViewModel
) {
    val run = detail.run
    val polylinePoints = detail.points.map { LatLng(it.latitude, it.longitude) }
    val scope = rememberCoroutineScope()

    val cameraPositionState = rememberCameraPositionState {
        if (polylinePoints.isNotEmpty()) {
            position = CameraPosition.fromLatLngZoom(polylinePoints.first(), 15f)
        }
    }

    var selectedPhoto by remember { mutableStateOf<Photo?>(null) }
    var showTitleDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var titleDraft by remember(run.id) { mutableStateOf(run.title) }
    var isSavingTitle by remember { mutableStateOf(false) }
    var titleSaveError by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    val dateStr = runCatching { dateFormat.format(run.startTime.toDate()) }.getOrDefault("")
    val subtitle = if (run.title.isBlank()) dateStr else "${run.type.label} • $dateStr"
    val canManage = detail.canManage

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
                if (isMapsConfigured()) {
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
                } else {
                    MapFallback(
                        points = polylinePoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        title = "Route map unavailable",
                        message = "This build is missing MAPS_API_KEY, so Google Maps cannot render on the detail screen."
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = run.displayTitle(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (canManage) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalIconButton(
                                    enabled = !isDeleting,
                                    onClick = {
                                        titleDraft = run.title
                                        titleSaveError = null
                                        showTitleDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit activity title"
                                    )
                                }
                                FilledTonalIconButton(
                                    enabled = !isSavingTitle && !isDeleting,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    onClick = {
                                        deleteError = null
                                        showDeleteDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete activity"
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (!canManage) {
                            "You can view this shared activity, but only its owner can edit or delete it."
                        } else if (run.title.isBlank()) {
                            "Add a custom title to make this ${run.type.label.lowercase(Locale.getDefault())} easier to spot later."
                        } else {
                            "Custom title saved for this ${run.type.label.lowercase(Locale.getDefault())}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    deleteError?.let { message ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
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
                    // Replaced LazyVerticalGrid with a simple Column/Row to avoid nested scroll issues
                    // and potential crashes if the grid height calculation fails in some contexts
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detail.photos.chunked(3).forEach { chunk ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                chunk.forEach { photo ->
                                    AsyncImage(
                                        model = photo.imageUrl,
                                        contentDescription = "Route photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedPhoto = photo }
                                    )
                                }
                                // Fill empty space if row is not full
                                repeat(3 - chunk.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
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

    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSavingTitle && !isDeleting) {
                    showTitleDialog = false
                    titleSaveError = null
                }
            },
            title = { Text("Edit activity title") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = titleDraft,
                        onValueChange = {
                            titleDraft = it
                            titleSaveError = null
                        },
                        label = { Text("Activity title") },
                        placeholder = { Text(run.type.label) },
                        singleLine = true,
                        enabled = !isSavingTitle && !isDeleting
                    )
                    Text(
                        text = "Leave it blank to fall back to the default ${run.type.label.lowercase(Locale.getDefault())} label.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    titleSaveError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSavingTitle && !isDeleting,
                    onClick = {
                        scope.launch {
                            isSavingTitle = true
                            titleSaveError = null
                            val result = viewModel.updateRunTitle(titleDraft)
                            isSavingTitle = false
                            result.onSuccess {
                                showTitleDialog = false
                                onRunUpdated()
                            }.onFailure { error ->
                                titleSaveError = error.message ?: "Failed to save title"
                            }
                        }
                    }
                ) {
                    if (isSavingTitle) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTitleDialog = false
                        titleSaveError = null
                    },
                    enabled = !isSavingTitle && !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteDialog = false
                    deleteError = null
                }
            },
            title = { Text("Delete activity?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (detail.photos.isNotEmpty()) {
                            "This will permanently delete this ${run.type.label.lowercase(Locale.getDefault())} and its ${detail.photos.size} attached photo${if (detail.photos.size == 1) "" else "s"}."
                        } else {
                            "This will permanently delete this ${run.type.label.lowercase(Locale.getDefault())}."
                        }
                    )
                    deleteError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        scope.launch {
                            isDeleting = true
                            deleteError = null
                            val result = viewModel.deleteRun()
                            isDeleting = false
                            result.onSuccess {
                                showDeleteDialog = false
                                onRunUpdated()
                                onBack()
                            }.onFailure { error ->
                                deleteError = error.message ?: "Failed to delete activity"
                            }
                        }
                    }
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        showDeleteDialog = false
                        deleteError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Two-row stat layout for the route detail page.
 * Grouping the values in a helper keeps the main screen focused on data flow
 * while this function handles the visual arrangement of summary metrics.
 */
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

/**
 * Small reusable metric card used inside the detail stat grid.
 * The helper keeps typography and padding consistent across all route stats.
 */
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
