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

/*
 * ViewModel layer for the live tracking flow.
 * It forwards commands to the foreground service and exposes service state to
 * Compose so screens can survive recreation without losing the session.
 */
data class SessionSnapshot(
    val type: ActivityType,
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
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Locally tracked type before service starts
    private var _pendingType = ActivityType.RUN

    val activityType: StateFlow<ActivityType> = TrackingService.activityType
    val isTracking: StateFlow<Boolean> = TrackingService.isTracking
    val routePoints: StateFlow<List<RoutePoint>> = TrackingService.routePoints
    val distanceMetres: StateFlow<Float> = TrackingService.distanceMetres
    val currentPaceSec: StateFlow<Float> = TrackingService.currentPaceSec
    val elapsedMs: StateFlow<Long> = TrackingService.elapsedMs
    val stepCount: StateFlow<Int> = TrackingService.stepCount
    val cadence: StateFlow<Float> = TrackingService.cadence

    fun setActivityType(type: ActivityType) {
        _pendingType = type
    }

    /** Sends ACTION_START to TrackingService with the chosen activity type. */
    fun startRun() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
            putExtra(TrackingService.EXTRA_ACTIVITY_TYPE, _pendingType.name)
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

    fun getEncodedPolyline(): String {
        val points = TrackingService.routePoints.value
        if (points.size < 2) return ""
        val latLngs = points.map { LatLng(it.latitude, it.longitude) }
        return PolylineEncoder.encode(latLngs)
    }

    fun getAveragePaceSec(): Float {
        val distKm = TrackingService.distanceMetres.value / 1000f
        if (distKm <= 0f) return 0f
        val totalSeconds = TrackingService.elapsedMs.value / 1000f
        return totalSeconds / distKm
    }

    /**
     * Captures the current session state.
     */
    fun sessionSnapshot(): SessionSnapshot {
        val duration = TrackingService.elapsedMs.value
        return SessionSnapshot(
            type = TrackingService.activityType.value, // Read the type directly from the service
            startTime = com.google.firebase.Timestamp((System.currentTimeMillis() - duration) / 1000, 0),
            endTime = com.google.firebase.Timestamp(System.currentTimeMillis() / 1000, 0),
            durationMs = duration,
            distanceMetres = TrackingService.distanceMetres.value,
            avgPaceSecPerKm = getAveragePaceSec(),
            bestPaceSecPerKm = getAveragePaceSec(),
            stepCount = TrackingService.stepCount.value,
            avgCadence = TrackingService.cadence.value,
            elevationGain = 0f,
            encodedPolyline = getEncodedPolyline(),
            routePoints = TrackingService.routePoints.value
        )
    }

    /**
     * Resets all TrackingService state.
     */
    fun resetSession() {
        TrackingService.resetState()
    }
}
