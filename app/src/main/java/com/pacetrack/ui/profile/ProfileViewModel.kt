package com.pacetrack.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pacetrack.data.model.User
import com.pacetrack.data.model.repository.RunRepository
import com.pacetrack.data.model.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel backing the Profile page and social interactions.
 * It keeps the signed-in user's profile, search results, and follow state in
 * one place so the Compose screen only reacts to observable state changes.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val runRepository: RunRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    // The authenticated user's own profile data
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Data for a profile being visited (for social/search)
    private val _viewedUser = MutableStateFlow<User?>(null)
    val viewedUser: StateFlow<User?> = _viewedUser.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadCurrentUser()
    }

    /**
     * Loads the profile of the currently logged-in user.
     */
    fun loadCurrentUser() {
        viewModelScope.launch {
            _currentUser.value = userRepository.getCurrentUser()
        }
    }

    /**
     * Loads a specific user's profile and checks if the current user follows them.
     * Used when clicking on a user in the search results or social feed.
     */
    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            _viewedUser.value = userRepository.getUser(userId)
            _isFollowing.value = userRepository.isFollowing(userId)
        }
    }

    /**
     * Starts following another user and refreshes the current profile.
     * The refresh is important because the Profile screen derives button state
     * from the authenticated user's following list, not from cached optimism.
     */
    fun followUser(targetUserId: String) {
        viewModelScope.launch {
            userRepository.followUser(targetUserId)
            _isFollowing.value = true
            loadCurrentUser() // Refresh local following list
        }
    }

    /**
     * Stops following another user and then reloads local profile state.
     * Re-fetching avoids mismatches between Firestore and the search list if
     * the user taps follow actions quickly.
     */
    fun unfollowUser(targetUserId: String) {
        viewModelScope.launch {
            userRepository.unfollowUser(targetUserId)
            _isFollowing.value = false
            loadCurrentUser() // Refresh local following list
        }
    }

    /**
     * Searches by display name as the user types.
     * Blank queries short-circuit locally so the repository is only called
     * when there is enough text to produce a meaningful result set.
     */
    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = userRepository.searchUsers(query)
            _isLoading.value = false
        }
    }
}
