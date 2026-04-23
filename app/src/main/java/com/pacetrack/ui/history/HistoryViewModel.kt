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

/*
 * State owner for the History page.
 * It requests the authenticated user's saved runs and exposes a single flow
 * that the UI can pattern-match into loading, content, or error states.
 */
/**
 * Representing the different states of the History screen.
 * This ensures the UI can easily react to loading or error scenarios.
 */
sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Success(val runs: List<Run>) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}

/**
 * ViewModel for loading the signed-in user's run history.
 * It asks the repository for runs tied to the current auth session and
 * converts the result into screen-friendly state updates.
 */
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
     * StateFlow keeps the latest result across recomposition and rotation,
     * while the auth guard prevents the repository from querying with a
     * missing user id after sign-out or failed session restore.
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
