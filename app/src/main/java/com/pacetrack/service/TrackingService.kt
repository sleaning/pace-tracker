package com.pacetrack.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.location.Location
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.pacetrack.MainActivity
import com.pacetrack.data.model.RoutePoint
import com.pacetrack.util.DistanceFormatter
import com.pacetrack.util.PaceFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


/**
 * TrackingService
 *
 * A Foreground Service that owns all GPS logic during an active run.
 * Running as a foreground service prevents Android from killing the process
 * when the user leaves the app or the screen turns off.
 *
 * All live data is exposed via companion-object StateFlows so TrackingViewModel
 * can observe without binding — this means Activity rotation has zero effect.
 *
 * Phase 4 will add SensorManager step counting inside this same service.
 * The stepCount and cadence StateFlows are already wired and ready.
 */
class TrackingService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID      = "pacetrack_tracking_channel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.pacetrack.ACTION_START"
        const val ACTION_STOP  = "com.pacetrack.ACTION_STOP"

        // Smooth pace over last 5 GPS segments to reduce noise
        private const val PACE_SMOOTH_WINDOW = 5

        // ── StateFlows observed by TrackingViewModel ──────────────────────────

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
        /** Every GPS coordinate recorded this session, in order. */
        val routePoints: StateFlow<List<RoutePoint>> = _routePoints.asStateFlow()

        private val _distanceMetres = MutableStateFlow(0f)
        /** Cumulative distance in metres. */
        val distanceMetres: StateFlow<Float> = _distanceMetres.asStateFlow()

        private val _currentPaceSec = MutableStateFlow(0f)
        /** Current pace in seconds-per-km, smoothed over last 5 segments. */
        val currentPaceSec: StateFlow<Float> = _currentPaceSec.asStateFlow()

        private val _elapsedMs = MutableStateFlow(0L)
        /** Elapsed time in milliseconds since the session started. */
        val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

        // Phase 4 hooks — populated by SensorManager in Phase 4
        private val _stepCount = MutableStateFlow(0)
        val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

        private val _cadence = MutableStateFlow(0f)
        val cadence: StateFlow<Float> = _cadence.asStateFlow()

        /**
         * Resets all state back to zero.
         * Called by TrackingViewModel AFTER it has read the final values
         * to build the PostRunSummary — not called on service stop itself.
         */
        fun resetState() {
            _isTracking.value     = false
            _routePoints.value    = emptyList()
            _distanceMetres.value = 0f
            _currentPaceSec.value = 0f
            _elapsedMs.value      = 0L
            _stepCount.value      = 0
            _cadence.value        = 0f
        }
    }

    // ── Instance fields ───────────────────────────────────────────────────────

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationManager: NotificationManager

    private var sessionStartMs = 0L

    // Rolling buffer of recent Location objects used to smooth pace
    private val recentLocations = ArrayDeque<Location>(PACE_SMOOTH_WINDOW + 1)

    // Phase 4 — Step counter fields
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var stepBaseline: Int = -1
    private var runStartTimeMs: Long = 0L

    private val handler = Handler(Looper.getMainLooper())

    // Updates elapsed time and refreshes the notification every second
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (_isTracking.value) {
                _elapsedMs.value = System.currentTimeMillis() - sessionStartMs
                updateNotification()
                handler.postDelayed(this, 1_000)
            }
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP  -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    // ── Start / stop ──────────────────────────────────────────────────────────

    private fun startTracking() {
        if (_isTracking.value) return
        sessionStartMs = System.currentTimeMillis()
        _isTracking.value = true
        recentLocations.clear()

        // startForeground() must be called immediately on Android 8+
        startForeground(NOTIFICATION_ID, buildNotification())
        requestLocationUpdates()
        handler.post(tickRunnable)

        // register step counter
        stepBaseline = -1
        runStartTimeMs = sessionStartMs
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun stopTracking() {
        _isTracking.value = false
        handler.removeCallbacks(tickRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        // State is NOT reset here — TrackingViewModel reads final values first
    }

    // ── Step Counter ──────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return

        val totalSteps = event.values[0].toInt()

        // Handle phone reboot mid-run — sensor resets to a small number
        if (stepBaseline == -1 || totalSteps < stepBaseline) {
            stepBaseline = totalSteps
        }

        val sessionSteps = totalSteps - stepBaseline
        val elapsedMinutes = (System.currentTimeMillis() - runStartTimeMs) / 60_000f
        val cadence = if (elapsedMinutes > 0) sessionSteps / elapsedMinutes else 0f

        _stepCount.value = sessionSteps
        _cadence.value = cadence
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    // ── Location ──────────────────────────────────────────────────────────────

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    onNewLocation(location)
                }
            }
        }
    }

    @Suppress("MissingPermission") // Permission checked in PreRunScreen before service starts
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3_000L // update every 3 seconds
        ).setMinUpdateIntervalMillis(2_000L)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    /**
     * Called on every new GPS fix.
     * 1. Appends a RoutePoint to the list.
     * 2. Accumulates distance from the previous point.
     * 3. Recalculates smoothed pace from recent segments.
     */
    private fun onNewLocation(location: Location) {
        // 1. Append RoutePoint
        val newPoint = RoutePoint(
            latitude  = location.latitude,
            longitude = location.longitude,
            altitude  = location.altitude,
            timestamp = location.time
        )
        _routePoints.value = _routePoints.value + newPoint

        // 2. Accumulate distance
        if (recentLocations.isNotEmpty()) {
            val prev = recentLocations.last()
            val results = FloatArray(1)
            Location.distanceBetween(
                prev.latitude, prev.longitude,
                location.latitude, location.longitude,
                results
            )
            _distanceMetres.value += results[0]
        }

        // 3. Update pace smoothing buffer
        recentLocations.addLast(location)
        if (recentLocations.size > PACE_SMOOTH_WINDOW + 1) {
            recentLocations.removeFirst()
        }
        _currentPaceSec.value = calculateSmoothedPace()
    }

    /**
     * Calculates pace in seconds-per-km averaged across the recent location buffer.
     * Returns 0f if there aren't enough points yet or the user is standing still.
     */
    private fun calculateSmoothedPace(): Float {
        if (recentLocations.size < 2) return 0f

        var totalDistM  = 0f
        var totalTimeMs = 0L

        for (i in 1 until recentLocations.size) {
            val a = recentLocations[i - 1]
            val b = recentLocations[i]
            val results = FloatArray(1)
            Location.distanceBetween(
                a.latitude, a.longitude,
                b.latitude, b.longitude,
                results
            )
            totalDistM  += results[0]
            totalTimeMs += (b.time - a.time)
        }

        if (totalDistM < 1f || totalTimeMs <= 0L) return 0f

        // Convert to seconds per km
        val secondsPerMetre = (totalTimeMs / 1000f) / totalDistM
        return secondsPerMetre * 1000f
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Run Tracking",
            NotificationManager.IMPORTANCE_LOW  // LOW = no sound, stays silent
        ).apply { description = "Shows live stats while a run is in progress" }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Run in progress")
        .setContentText(notificationText())
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openAppIntent())
        .build()

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun notificationText(): String {
        val dist    = DistanceFormatter.format(_distanceMetres.value)
        val elapsed = PaceFormatter.formatElapsed(_elapsedMs.value)
        return "$dist • $elapsed"
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}