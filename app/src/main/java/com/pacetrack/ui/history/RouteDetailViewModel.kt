package com.pacetrack.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pacetrack.data.model.Photo
import com.pacetrack.data.model.RoutePoint
import com.pacetrack.data.model.Run
import com.pacetrack.data.model.repository.RunRepository
import com.pacetrack.util.PolylineEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
 * ViewModel for reconstructing a completed run on the detail page.
 * It loads the saved Run, decodes its compact polyline, and fetches any
 * attached photos so the screen can render one combined detail model.
 */
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

/**
 * ViewModel for the saved route detail screen.
 * It rebuilds everything needed to render a completed activity, including
 * the run summary, decoded path points, and any photos linked to it.
 */
@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    private val runRepository: RunRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val runId: String = checkNotNull(savedStateHandle["runId"])

    private val _uiState = MutableStateFlow<RouteDetailUiState>(RouteDetailUiState.Loading)
    val uiState: StateFlow<RouteDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    /**
     * Loads the run and its photos in parallel. The route points are decoded
     * from the polyline string already stored on the Run document — no separate
     * Firestore read needed, which is faster AND avoids the empty-subcollection
     * problem (we never wrote to runs/{id}/points in the first place).
     */
    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = RouteDetailUiState.Loading
            try {
                val run = runRepository.getRunById(runId)
                    ?: throw IllegalStateException("Run not found")

                _uiState.value = RouteDetailUiState.Success(
                    RouteDetail(
                        run = run,
                        points = decodePointsSafely(run),
                        photos = loadPhotosSafely(run)
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = RouteDetailUiState.Error(e.message ?: "Failed to load route")
            }
        }
    }

    /**
     * Retrieves photos using whichever saved linkage is available.
     * Newer runs can use explicit photo ids, while older data still falls
     * back to querying by run id without breaking the detail screen.
     */
    private suspend fun loadPhotosSafely(run: Run): List<Photo> {
        return runCatching {
            if (run.photoIds.isNotEmpty()) {
                runRepository.getPhotosByIds(run.photoIds)
            } else {
                runRepository.getPhotosForRun(run.id)
            }
        }
            .getOrElse { emptyList() }
    }

    /**
     * Rebuilds route points from the encoded polyline stored on the run.
     * Decoding is wrapped defensively so one malformed polyline does not
     * prevent the rest of the run details from rendering.
     */
    private fun decodePointsSafely(run: Run): List<RoutePoint> {
        if (run.encodedPolyline.isBlank()) return emptyList()

        return runCatching {
            PolylineEncoder.decode(run.encodedPolyline).map { latLng ->
                RoutePoint(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
            }
        }.getOrElse { emptyList() }
    }
}
