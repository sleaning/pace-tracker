package com.pacetrack.ui.history

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pacetrack.data.model.Photo
import com.pacetrack.data.model.RoutePoint
import com.pacetrack.data.model.Run
import com.pacetrack.data.model.repository.PhotoRepository
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
    val photos: List<Photo>,
    val canManage: Boolean
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
    private val photoRepository: PhotoRepository,
    private val auth: FirebaseAuth,
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
                        photos = loadPhotosSafely(run),
                        canManage = isOwnedByCurrentUser(run)
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = RouteDetailUiState.Error(e.message ?: "Failed to load route")
            }
        }
    }

    suspend fun updateRunTitle(title: String): Result<Unit> {
        val currentState = _uiState.value as? RouteDetailUiState.Success
            ?: return Result.failure(IllegalStateException("Run detail is not loaded"))
        if (!currentState.detail.canManage) {
            return Result.failure(IllegalStateException("You can only edit your own activities."))
        }

        val normalizedTitle = title.trim()
        if (currentState.detail.run.title == normalizedTitle) {
            return Result.success(Unit)
        }

        return runCatching {
            runRepository.updateRunTitle(runId, normalizedTitle)
            _uiState.value = RouteDetailUiState.Success(
                currentState.detail.copy(
                    run = currentState.detail.run.copy(title = normalizedTitle)
                )
            )
        }.mapActionErrors("edit this activity")
    }

    suspend fun deleteRun(): Result<Unit> {
        val currentState = _uiState.value as? RouteDetailUiState.Success
            ?: return Result.failure(IllegalStateException("Run detail is not loaded"))
        if (!currentState.detail.canManage) {
            return Result.failure(IllegalStateException("You can only delete your own activities."))
        }

        return runCatching {
            val photos = loadPhotosSafely(currentState.detail.run)
            photos.forEach { photo ->
                photoRepository.deletePhoto(photo)
            }
            runRepository.deleteRun(runId)
        }.mapActionErrors("delete this activity")
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

    private fun isOwnedByCurrentUser(run: Run): Boolean = auth.currentUser?.uid == run.userId

    private fun Result<Unit>.mapActionErrors(action: String): Result<Unit> {
        return fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                val friendlyError = if (
                    error is FirebaseFirestoreException &&
                    error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) {
                    IllegalStateException(
                        "Firebase blocked this request. Make sure your Firestore rules allow the signed-in owner to $action."
                    )
                } else {
                    error
                }
                Result.failure(friendlyError)
            }
        )
    }
}
