package com.pacetrack.ui.tracking

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.pacetrack.data.model.ActivityType
import com.pacetrack.data.model.RoutePoint
import com.pacetrack.service.TrackingService
import com.pacetrack.util.PolylineEncoder
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class SessionSnapshot(
    val startTime: com.google.firebase.Timestamp,
    val endTime: com.google.firebase.Timestamp,
    val durationMs: Long,
    val distanceMetres: Float,
    val avgPaceSecPerKm: Float,
    val bestPaceSecPerKm: Float,
    val stepCount: Int,
    val avgCadence: Float,
    val elevationGain: Float,
    val encodedPolyline: String,
    val routePoints: List<com.pacetrack.data.model.RoutePoint>
)

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
 * We expose the Service flows directly to ensure that when a new ViewModel
 * instance is created (e.g. on the Summary screen), it immediately sees
 * the current values instead of starting at zero.
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
    val routePoints: StateFlow<List<RoutePoint>> = TrackingService.routePoints
    val distanceMetres: StateFlow<Float> = TrackingService.distanceMetres
    val currentPaceSec: StateFlow<Float> = TrackingService.currentPaceSec
    val elapsedMs: StateFlow<Long> = TrackingService.elapsedMs
    val stepCount: StateFlow<Int> = TrackingService.stepCount
    val cadence: StateFlow<Float> = TrackingService.cadence

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
     * Captures the current session state as an immutable snapshot.
     * Called by PostRunSummaryScreen to display and then save final values.
     */
    fun sessionSnapshot(): SessionSnapshot {
        val duration = elapsedMs.value
        return SessionSnapshot(
            startTime = com.google.firebase.Timestamp((System.currentTimeMillis() - duration) / 1000, 0),
            endTime = com.google.firebase.Timestamp(System.currentTimeMillis() / 1000, 0),
            durationMs = duration,
            distanceMetres = distanceMetres.value,
            avgPaceSecPerKm = getAveragePaceSec(),
            bestPaceSecPerKm = getAveragePaceSec(),
            stepCount = stepCount.value,
            avgCadence = cadence.value,
            elevationGain = 0f,
            encodedPolyline = getEncodedPolyline(),
            routePoints = routePoints.value
        )
    }

    /**
     * Resets all TrackingService state.
     * Called AFTER PostRunSummaryScreen has read and saved the final values.
     */
    fun resetSession() {
        TrackingService.resetState()
    }
}
