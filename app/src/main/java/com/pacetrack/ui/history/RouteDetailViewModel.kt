package com.pacetrack.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pacetrack.data.model.Photo
import com.pacetrack.data.model.RoutePoint
import com.pacetrack.data.model.Run
import com.pacetrack.data.model.repository.RunRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RouteDetail(
    val run: Run,
    val points: List<RoutePoint>,
    val photos: List<Photo>
)

sealed class RouteDetailUiState {
    object Loading : RouteDetailUiState()
    data class Success(val detail: RouteDetail) : RouteDetailUiState()
    data class Error(val message: String) : RouteDetailUiState()
}

@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    private val runRepository: RunRepository,
    savedStateHandle: SavedStateHandle          // Hilt injects nav args here
) : ViewModel() {

    // The runId nav argument must be registered as a named argument in AppNavGraph:
    // composable("route_detail/{runId}") { ... }
    private val runId: String = checkNotNull(savedStateHandle["runId"])

    private val _uiState = MutableStateFlow<RouteDetailUiState>(RouteDetailUiState.Loading)
    val uiState: StateFlow<RouteDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    /**
     * Loads the run, its GPS route points, and its photos in parallel using
     * coroutine async/await — three Firestore reads at once instead of three
     * sequential awaits. Exposes as a single RouteDetail object in StateFlow.
     *
     * StateFlow survives screen rotation: the data is fetched once when
     * the ViewModel is first created, then reused on every recomposition.
     */
    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = RouteDetailUiState.Loading
            try {
                // Parallel reads — all three fire at the same time
                val runDeferred = async { runRepository.getRunById(runId) }
                val pointsDeferred = async { runRepository.getRoutePoints(runId) }
                val photosDeferred = async { runRepository.getPhotosForRun(runId) }

                val run = runDeferred.await()
                    ?: throw IllegalStateException("Run not found")
                val points = pointsDeferred.await()
                val photos = photosDeferred.await()

                _uiState.value = RouteDetailUiState.Success(
                    RouteDetail(run, points, photos)
                )
            } catch (e: Exception) {
                _uiState.value = RouteDetailUiState.Error(e.message ?: "Failed to load route")
            }
        }
    }
}
