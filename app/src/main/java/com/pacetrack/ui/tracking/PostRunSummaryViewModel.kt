package com.pacetrack.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pacetrack.data.model.ActivityType
import com.pacetrack.data.model.Run
import com.pacetrack.data.model.repository.RunRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Saved : SaveState()
    data class Error(val message: String) : SaveState()
}

@HiltViewModel
class PostRunSummaryViewModel @Inject constructor(
    private val runRepository: RunRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun saveRun(activityType: ActivityType, snapshot: SessionSnapshot) {
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            _saveState.value = SaveState.Error("User not authenticated. Please sign in again.")
            return
        }

        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                val run = Run(
                    userId = userId,
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
                    isPublic = true
                )
                runRepository.saveRun(run)
                _saveState.value = SaveState.Saved
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "Failed to save run to Firestore")
            }
        }
    }
}
