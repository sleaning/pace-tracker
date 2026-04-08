package com.pacetrack.ui.tracking

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.pacetrack.data.model.ActivityType
import com.google.accompanist.permissions.isGranted


/**
 * PreRunScreen
 *
 * Shown before a run starts. Lets the user pick Walk or Run, checks that
 * location permissions are granted, then navigates to ActiveTrackingScreen.
 *
 * Permission flow:
 *  1. ACCESS_FINE_LOCATION — requested on this screen.
 *  2. ACCESS_BACKGROUND_LOCATION — requested separately on Android 10+ after
 *     foreground permission is granted (OS requirement).
 *  3. POST_NOTIFICATIONS — requested on Android 13+.
 *
 * The Start button is disabled until fine location is granted.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PreRunScreen(
    onStartRun: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    // Build the list of permissions we need to request
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val permissionsState = rememberMultiplePermissionsState(permissions)
    val locationGranted = permissionsState.permissions
        .any { it.permission == Manifest.permission.ACCESS_FINE_LOCATION && it.status.isGranted }

    var selectedType by remember { mutableStateOf(ActivityType.RUN) }

    // Request permissions as soon as the screen appears
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Ready to go?",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose your activity and hit Start",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ── Activity type toggle ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActivityTypeCard(
                label = "Run",
                icon = { Icon(Icons.Filled.DirectionsRun, contentDescription = "Run") },
                selected = selectedType == ActivityType.RUN,
                onClick = { selectedType = ActivityType.RUN },
                modifier = Modifier.weight(1f)
            )
            ActivityTypeCard(
                label = "Walk",
                icon = { Icon(Icons.Filled.DirectionsWalk, contentDescription = "Walk") },
                selected = selectedType == ActivityType.WALK,
                onClick = { selectedType = ActivityType.WALK },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // ── Permission warning ────────────────────────────────────────────────
        if (!locationGranted) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Location permission is required to track your route.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Start button ──────────────────────────────────────────────────────
        Button(
            onClick = {
                viewModel.setActivityType(selectedType)
                viewModel.startRun()
                onStartRun()
            },
            enabled = locationGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Start ${selectedType.name.lowercase().replaceFirstChar { it.uppercase() }}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Grant permission button shown when denied
        if (!locationGranted) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Location Permission")
            }
        }
    }
}

@Composable
private fun ActivityTypeCard(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val contentColor = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}