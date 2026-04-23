package com.pacetrack.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    val feedRuns by viewModel.feedRuns.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

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
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ForestGreen)
            }
        } else if (feedRuns.isEmpty()) {
            EmptyFeedMessage(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(feedRuns, key = { it.id }) { run ->
                    FeedCard(run = run, onClick = { onRunClick(run.id) })
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
