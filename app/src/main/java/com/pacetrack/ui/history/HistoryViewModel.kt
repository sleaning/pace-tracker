package com.pacetrack.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pacetrack.data.model.Run
import com.pacetrack.data.model.repository.RunRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Representing the different states of the History screen.
 * This ensures the UI can easily react to loading or error scenarios.
 */
sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Success(val runs: List<Run>) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val runRepository: RunRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadRuns()
    }

    /**
     * Fetches the user's runs and updates the UI state.
     * * By using StateFlow within the ViewModel, the data survives configuration changes
     * (like screen rotations), preventing unnecessary network calls to Firebase.
     */
    fun loadRuns() {
        viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading

            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.value = HistoryUiState.Error("User not authenticated")
                return@launch
            }

            try {
                val runs = runRepository.getRuns(userId)
                _uiState.value = HistoryUiState.Success(runs)
            } catch (e: Exception) {
                _uiState.value = HistoryUiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }
}