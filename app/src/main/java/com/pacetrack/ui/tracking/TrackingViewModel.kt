package com.pacetrack.ui.tracking

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pacetrack.data.model.ActivityType
import com.pacetrack.data.model.RoutePoint
import com.pacetrack.service.TrackingService
import com.pacetrack.util.PolylineEncoder
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * TrackingViewModel
 *
 * Sits between the UI and TrackingService. It does NOT own any GPS logic —
 * all of that lives in TrackingService. This ViewModel simply:
 *
 *  1. Forwards start/stop commands to TrackingService via Intents.
 *  2. Exposes TrackingService's companion StateFlows to the Composables.
 *  3. Survives screen rotation — the service keeps running regardless.
 *  4. Builds the final run summary values when the user stops a run.
 *
 * No binding to the service is needed because all state is on the
 * companion object and collected here as StateFlows.
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Selected activity type — set on PreRunScreen before starting
    var activityType: ActivityType = ActivityType.RUN
        private set

    fun setActivityType(type: ActivityType) {
        activityType = type
    }

    // ── Live data forwarded from TrackingService ──────────────────────────────

    val isTracking: StateFlow<Boolean> = TrackingService.isTracking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val routePoints: StateFlow<List<RoutePoint>> = TrackingService.routePoints
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val distanceMetres: StateFlow<Float> = TrackingService.distanceMetres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    val currentPaceSec: StateFlow<Float> = TrackingService.currentPaceSec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    val elapsedMs: StateFlow<Long> = TrackingService.elapsedMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    // Phase 4 — step counter values already wired
    val stepCount: StateFlow<Int> = TrackingService.stepCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val cadence: StateFlow<Float> = TrackingService.cadence
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    // ── Commands ──────────────────────────────────────────────────────────────

    /** Sends ACTION_START to TrackingService, beginning GPS updates. */
    fun startRun() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    /** Sends ACTION_STOP to TrackingService, ending GPS updates. */
    fun stopRun() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * Builds the encoded polyline string from the current route points.
     * Called by PostRunSummaryScreen before saving to Firestore.
     */
    fun getEncodedPolyline(): String {
        val latLngs = routePoints.value.map { LatLng(it.latitude, it.longitude) }
        return PolylineEncoder.encode(latLngs)
    }

    /**
     * Calculates average pace across the whole run.
     * Returns 0f if no distance was covered.
     */
    fun getAveragePaceSec(): Float {
        val distKm = distanceMetres.value / 1000f
        if (distKm <= 0f) return 0f
        val totalSeconds = elapsedMs.value / 1000f
        return totalSeconds / distKm
    }

    /**
     * Resets all TrackingService state.
     * Called AFTER PostRunSummaryScreen has read and saved the final values.
     */
    fun resetSession() {
        TrackingService.resetState()
    }
}