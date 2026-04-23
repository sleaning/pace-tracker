package com.pacetrack.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

private val ForestGreen = Color(0xFF2D6A4F) // Primary: Top app bar, primary buttons
private val SageGreen = Color(0xFF40916C)   // Secondary: Icons, subheadings
private val DeepForest = Color(0xFF1B4332)  // Headings, page titles
private val MutedText = Color(0xFF4A7A5C)   // Secondary labels, timestamps
private val OffWhite = Color(0xFFF5F7F2)    // App background

/**
 * Social feed page showing recent public runs from followed users.
 * The screen reacts to loading state from HomeViewModel and either renders
 * a progress indicator, an empty state, or tappable feed cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRunClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        containerColor = OffWhite, // Set to official app background
        topBar = {
            TopAppBar(
                title = { Text("Activity Feed") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreen, // Official Primary color
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (isLoading && feedItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ForestGreen)
            }
        } else if (errorMessage != null && feedItems.isEmpty()) {
            FeedErrorMessage(
                message = errorMessage ?: "Failed to load activity feed",
                onRetry = { viewModel.loadFeed(force = true) },
                modifier = Modifier.padding(padding)
            )
        } else if (feedItems.isEmpty()) {
            EmptyFeedMessage(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (errorMessage != null) {
                    item {
                        FeedWarningCard(
                            message = errorMessage ?: "Activity feed may be incomplete",
                            onRetry = { viewModel.loadFeed(force = true) },
                            isRetrying = isLoading
                        )
                    }
                }
                items(feedItems, key = { it.run.id }) { item ->
                    FeedCard(item = item, onClick = { onRunClick(item.run.id) })
                }
            }
        }
    }
}

/**
 * Empty-state content for users who are not following anyone yet.
 * This keeps the feed page informative instead of looking broken when the
 * backend correctly returns an empty list of runs.
 */
@Composable
private fun EmptyFeedMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Group,
            contentDescription = null,
            tint = SageGreen, // Official Secondary/Icon color
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No activity yet",
            style = MaterialTheme.typography.titleMedium,
            color = DeepForest // Official Heading color
        )
        Text(
            text = "Follow friends to see their runs here",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText // Official secondary label color
        )
    }
}

@Composable
private fun FeedErrorMessage(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Couldn’t load activity",
            style = MaterialTheme.typography.titleMedium,
            color = DeepForest
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)) {
            Text("Retry")
        }
    }
}

@Composable
private fun FeedWarningCard(
    message: String,
    onRetry: () -> Unit,
    isRetrying: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry, enabled = !isRetrying) {
                if (isRetrying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Retrying...")
                    }
                } else {
                    Text("Retry")
                }
            }
        }
    }
}
