package com.pacetrack.ui.tracking

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pacetrack.data.model.ActivityType
import com.pacetrack.data.model.Run
import com.pacetrack.data.model.repository.PhotoRepository
import com.pacetrack.data.model.repository.RunRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsible for turning a finished session into saved data.
 * It keeps temporary photo selections in memory and coordinates the final
 * upload-and-save sequence once the user confirms the run summary.
 */
sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Saved : SaveState()
    data class Error(val message: String) : SaveState()
}

@HiltViewModel
class PostRunSummaryViewModel @Inject constructor(
    private val runRepository: RunRepository,
    private val photoRepository: PhotoRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /** Local Uris for photos the user has added but not yet uploaded. */
    private val _pendingPhotos = MutableStateFlow<List<Uri>>(emptyList())
    val pendingPhotos: StateFlow<List<Uri>> = _pendingPhotos.asStateFlow()

    /**
     * Adds a locally selected image to the pending queue.
     * The Uri is kept local until save time so users can remove photos
     * without leaving orphaned uploads behind in Firebase Storage.
     */
    fun addPhoto(uri: Uri) {
        _pendingPhotos.value = _pendingPhotos.value + uri
    }

    /**
     * Removes a photo from the pending queue before upload.
     * Because uploads have not started yet, this is just a local list update.
     */
    fun removePhoto(uri: Uri) {
        _pendingPhotos.value = _pendingPhotos.value - uri
    }

    /**
     * Saves the Run and uploads all pending photos.
     *
     * Flow:
     *  1. Generate a runId up front (so photos can reference it before save).
     *  2. Upload each photo to Storage + create its Firestore Photo doc.
     *  3. Save the Run doc with photoIds populated.
     *
     * If any photo upload fails, the whole save fails — that's intentional:
     * we'd rather the user retry than ship a run missing photos they added.
     */
    fun saveRun(
        context: Context,
        activityType: ActivityType,
        snapshot: SessionSnapshot,
        title: String
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _saveState.value = SaveState.Error("User not authenticated. Please sign in again.")
            return
        }

        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                // Photos are tagged with the final known route point so the
                // route detail page can place them back on the finished map.
                val lastPoint = snapshot.routePoints.lastOrNull()
                val photoLat = lastPoint?.latitude ?: 0.0
                val photoLng = lastPoint?.longitude ?: 0.0

                val photoIds = mutableListOf<String>()
                // The run id is created first so every uploaded photo can point
                // at its future parent run document before that document exists.
                val runId = java.util.UUID.randomUUID().toString()

                for (uri in _pendingPhotos.value) {
                    val photoId = photoRepository.uploadPhoto(
                        context = context,
                        localUri = uri,
                        runId = runId,
                        userId = userId,
                        latitude = photoLat,
                        longitude = photoLng
                    )
                    photoIds.add(photoId)
                }

                // 2. Save the Run doc with the IDs attached
                val run = Run(
                    id = runId,
                    userId = userId,
                    title = title.trim(),
                    type = activityType,
                    startTime = snapshot.startTime,
                    endTime = snapshot.endTime,
                    duration = snapshot.durationMs,
                    distance = snapshot.distanceMetres,
                    avgPace = snapshot.avgPaceSecPerKm,
                    bestPace = snapshot.bestPaceSecPerKm,
                    stepCount = snapshot.stepCount,
                    avgCadence = snapshot.avgCadence,
                    elevationGain = snapshot.elevationGain,
                    encodedPolyline = snapshot.encodedPolyline,
                    photoIds = photoIds,
                    isPublic = true
                )
                runRepository.saveRun(run)

                // Clear local state
                _pendingPhotos.value = emptyList()
                _saveState.value = SaveState.Saved
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "Failed to save run")
            }
        }
    }
}
