package com.pacetrack.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.pacetrack.MainActivity
import com.pacetrack.data.model.ActivityType
import com.pacetrack.data.model.RoutePoint
import com.pacetrack.util.DistanceFormatter
import com.pacetrack.util.PaceFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "TrackingService"

/**
 * Foreground service that owns the active tracking session.
 * It listens to GPS and step sensor updates, keeps long-running state alive
 * off-screen, and publishes the latest values through shared StateFlows.
 */
class TrackingService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID      = "pacetrack_tracking_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.pacetrack.ACTION_START"
        const val ACTION_STOP  = "com.pacetrack.ACTION_STOP"
        const val EXTRA_ACTIVITY_TYPE = "EXTRA_ACTIVITY_TYPE"
        
        private const val PACE_SMOOTH_WINDOW = 5

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _activityType = MutableStateFlow(ActivityType.RUN)
        val activityType: StateFlow<ActivityType> = _activityType.asStateFlow()

        private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
        val routePoints: StateFlow<List<RoutePoint>> = _routePoints.asStateFlow()

        private val _distanceMetres = MutableStateFlow(0f)
        val distanceMetres: StateFlow<Float> = _distanceMetres.asStateFlow()

        private val _currentPaceSec = MutableStateFlow(0f)
        val currentPaceSec: StateFlow<Float> = _currentPaceSec.asStateFlow()

        private val _elapsedMs = MutableStateFlow(0L)
        val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

        private val _stepCount = MutableStateFlow(0)
        val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

        private val _cadence = MutableStateFlow(0f)
        val cadence: StateFlow<Float> = _cadence.asStateFlow()

        /**
         * Clears all shared session state after a run is saved or discarded.
         * The flows live in the companion object, so they must be reset
         * manually before the next session begins.
         */
        fun resetState() {
            Log.d(TAG, "resetState called")
            _isTracking.value     = false
            _activityType.value   = ActivityType.RUN
            _routePoints.value    = emptyList()
            _distanceMetres.value = 0f
            _currentPaceSec.value = 0f
            _elapsedMs.value      = 0L
            _stepCount.value      = 0
            _cadence.value        = 0f
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationManager: NotificationManager

    private var sessionStartMs = 0L
    private val recentLocations = ArrayDeque<Location>(PACE_SMOOTH_WINDOW + 1)
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var stepBaseline: Int = -1
    private var runStartTimeMs: Long = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (_isTracking.value) {
                val now = System.currentTimeMillis()
                _elapsedMs.value = now - sessionStartMs
                updateNotification()
                handler.postDelayed(this, 1_000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val typeName = intent.getStringExtra(EXTRA_ACTIVITY_TYPE) ?: ActivityType.RUN.name
                val type = try { ActivityType.valueOf(typeName) } catch (e: Exception) { ActivityType.RUN }
                startTracking(type)
            }
            ACTION_STOP  -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    /**
     * Starts the live session and moves the service into the foreground.
     */
    private fun startTracking(type: ActivityType) {
        if (_isTracking.value) return
        Log.d(TAG, "startTracking as ${type.name}")
        _activityType.value = type
        sessionStartMs = System.currentTimeMillis()
        _isTracking.value = true
        recentLocations.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        requestLocationUpdates()
        handler.post(tickRunnable)

        stepBaseline = -1
        runStartTimeMs = sessionStartMs
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /**
     * Stops sensors, location updates, and the foreground notification.
     */
    private fun stopTracking() {
        Log.d(TAG, "stopTracking. Final distance: ${_distanceMetres.value}, Duration: ${_elapsedMs.value}")
        _isTracking.value = false
        handler.removeCallbacks(tickRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val totalSteps = event.values[0].toInt()
        if (stepBaseline == -1 || totalSteps < stepBaseline) {
            stepBaseline = totalSteps
        }
        val sessionSteps = totalSteps - stepBaseline
        val elapsedMinutes = (System.currentTimeMillis() - runStartTimeMs) / 60_000f
        val cadenceVal = if (elapsedMinutes > 0) sessionSteps / elapsedMinutes else 0f
        _stepCount.value = sessionSteps
        _cadence.value = cadenceVal
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    onNewLocation(location)
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun onNewLocation(location: Location) {
        Log.d(TAG, "onNewLocation: lat=${location.latitude}, lng=${location.longitude}, acc=${location.accuracy}")
        val newPoint = RoutePoint(
            latitude  = location.latitude,
            longitude = location.longitude,
            altitude  = location.altitude,
            timestamp = location.time
        )
        
        // Ensure StateFlow detects change by creating new list
        _routePoints.value = _routePoints.value + newPoint

        if (recentLocations.isNotEmpty()) {
            val prev = recentLocations.last()
            val results = FloatArray(1)
            Location.distanceBetween(
                prev.latitude, prev.longitude,
                location.latitude, location.longitude,
                results
            )
            _distanceMetres.value += results[0]
            Log.d(TAG, "New distance: ${_distanceMetres.value}m (+${results[0]}m)")
        }

        recentLocations.addLast(location)
        if (recentLocations.size > PACE_SMOOTH_WINDOW + 1) {
            recentLocations.removeFirst()
        }
        _currentPaceSec.value = calculateSmoothedPace()
    }

    private fun calculateSmoothedPace(): Float {
        if (recentLocations.size < 2) return 0f
        var totalDistM  = 0f
        var totalTimeMs = 0L
        for (i in 1 until recentLocations.size) {
            val a = recentLocations[i - 1]
            val b = recentLocations[i]
            val results = FloatArray(1)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results)
            totalDistM  += results[0]
            totalTimeMs += (b.time - a.time)
        }
        if (totalDistM < 1f || totalTimeMs <= 0L) return 0f
        val secondsPerMetre = (totalTimeMs / 1000f) / totalDistM
        return secondsPerMetre * 1000f
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Activity Tracking", NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): android.app.Notification {
        val typeLabel = _activityType.value.name.lowercase().replaceFirstChar { it.uppercase() }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$typeLabel in progress")
            .setContentText("${DistanceFormatter.format(_distanceMetres.value)} • ${PaceFormatter.formatElapsed(_elapsedMs.value)}")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
