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
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val runRepository: RunRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val _runs = MutableStateFlow<List<Run>>(emptyList())
    val runs: StateFlow<List<Run>> = _runs

    fun loadRuns() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            _runs.value = runRepository.getRuns(userId)
        }
    }
}