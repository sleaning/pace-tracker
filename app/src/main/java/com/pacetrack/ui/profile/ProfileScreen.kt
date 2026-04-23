package com.pacetrack.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pacetrack.data.model.User
import com.pacetrack.ui.auth.AuthViewModel

private val ForestGreen = Color(0xFF2D6A4F)
private val SageGreen = Color(0xFF40916C)
private val DeepForest = Color(0xFF1B4332)
private val MutedText = Color(0xFF4A7A5C)
private val SurfaceGreen = Color(0xFFE9F3EC)
private val OffWhite = Color(0xFFF5F7F2)

/**
 * Profile and social discovery page for the current user.
 * It shows the signed-in athlete's summary, supports searching by name, and
 * exposes follow or unfollow actions for people returned in search results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val followedUsers by viewModel.followedUsers.collectAsStateWithLifecycle()
    val isLoadingFollowedUsers by viewModel.isLoadingFollowedUsers.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        containerColor = OffWhite,
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreen,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(
                        onClick = {
                            authViewModel.signOut()
                            onSignOut()
                        },
                        modifier = Modifier.semantics { contentDescription = "Sign out" }
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign out", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // My Profile Card
            item {
                currentUser?.let { user ->
                    UserHeader(user = user)
                }
            }

            if (currentUser != null) {
                item {
                    Text(
                        text = "Following",
                        style = MaterialTheme.typography.labelLarge,
                        color = DeepForest
                    )
                }

                if (isLoadingFollowedUsers && followedUsers.isEmpty()) {
                    item {
                        FollowingLoadingCard()
                    }
                } else if (followedUsers.isEmpty()) {
                    item {
                        EmptyFollowingCard()
                    }
                } else {
                    items(followedUsers, key = { it.id }) { user ->
                        UserSearchCard(
                            user = user,
                            isFollowing = true,
                            onFollow = {},
                            onUnfollow = { viewModel.unfollowUser(user.id) }
                        )
                    }
                }
            }

            // Search friends
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchUsers(it)
                    },
                    label = { Text("Find friends by name") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = MutedText
                    )
                )
            }

            // Results Section
            if (searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Search Results",
                        style = MaterialTheme.typography.labelLarge,
                        color = DeepForest
                    )
                }
                items(searchResults, key = { it.id }) { user ->
                    // Check if current user is following this person
                    val isThisUserFollowed = currentUser?.following?.contains(user.id) == true

                    UserSearchCard(
                        user = user,
                        isFollowing = isThisUserFollowed,
                        onFollow = { viewModel.followUser(user.id) },
                        onUnfollow = { viewModel.unfollowUser(user.id) }
                    )
                }
            } else if (searchQuery.isNotEmpty()) {
                item {
                    Text(
                        text = "No users found for '$searchQuery'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Header card for the signed-in user's own profile data.
 * This surfaces the information most relevant to the social features without
 * forcing the screen to build a more complex dedicated profile form yet.
 */
@Composable
private fun UserHeader(user: User) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceGreen),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(user.displayName, style = MaterialTheme.typography.headlineSmall, color = DeepForest)
                Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MutedText)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Following ${user.following.size} athletes",
                    style = MaterialTheme.typography.labelMedium,
                    color = SageGreen
                )
            }
        }
    }
}

@Composable
private fun FollowingLoadingCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = ForestGreen
            )
            Text(
                text = "Loading followed athletes...",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
        }
    }
}

@Composable
private fun EmptyFollowingCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = SageGreen,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = "You're not following anyone yet",
                    style = MaterialTheme.typography.titleSmall,
                    color = DeepForest
                )
                Text(
                    text = "Search for friends below to build your activity feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }
    }
}

/**
 * Search-result card used for follow management.
 * The button label and click target flip based on current relationship state,
 * which lets the screen stay declarative while the ViewModel handles updates.
 */
@Composable
private fun UserSearchCard(
    user: User,
    isFollowing: Boolean,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                tint = SageGreen,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium, color = DeepForest)
                Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
            Button(
                onClick = if (isFollowing) onUnfollow else onFollow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) SageGreen else ForestGreen
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.semantics {
                    contentDescription = if (isFollowing) "Unfollow ${user.displayName}" else "Follow ${user.displayName}"
                }
            ) {
                Text(if (isFollowing) "Following" else "Follow", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
