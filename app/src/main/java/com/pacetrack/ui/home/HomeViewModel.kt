package com.pacetrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pacetrack.data.model.Run
import com.pacetrack.data.model.repository.RunRepository
import com.pacetrack.data.model.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Home feed page.
 * It joins the current user's following list with the run repository so the
 * UI can render a social feed without talking to Firebase directly.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val runRepository: RunRepository
) : ViewModel() {

    private val _feedRuns = MutableStateFlow<List<Run>>(emptyList())
    val feedRuns: StateFlow<List<Run>> = _feedRuns.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadFeed()
    }

    /**
     * Loads public runs for the accounts the current user follows.
     * The view model first fetches the user profile because the following ids
     * live there, then uses those ids to request the feed content itself.
     */
    fun loadFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = userRepository.getCurrentUser()
                if (user != null && user.following.isNotEmpty()) {
                    _feedRuns.value = runRepository.getFollowingRuns(user.following)
                } else {
                    _feedRuns.value = emptyList()
                }
            } catch (e: Exception) {
                // handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
}
