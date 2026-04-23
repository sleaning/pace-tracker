package com.pacetrack.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/*
 * Core data models shared by the whole PaceTrack app.
 */

/**
 * One completed activity session (Walk or Run).
 * The 'type' field determines how it's labeled in the UI and history.
 */
data class Run(
    val id: String = "",
    val userId: String = "",
    val type: ActivityType = ActivityType.RUN,
    val startTime: Timestamp = Timestamp.now(),
    val endTime: Timestamp = Timestamp.now(),
    val duration: Long = 0L,
    val distance: Float = 0f,
    val avgPace: Float = 0f,
    val bestPace: Float = 0f,
    val stepCount: Int = 0,
    val avgCadence: Float = 0f,
    val elevationGain: Float = 0f,
    val encodedPolyline: String = "",
    val photoIds: List<String> = emptyList(),
    @get:PropertyName("public")
    @set:PropertyName("public")
    var isPublic: Boolean = true
) {
    // Required for Firestore deserialization
    constructor() : this(id = "")
}

data class RoutePoint(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val timestamp: Long = 0L
)

data class Photo(
    val id: String = "",
    val runId: String = "",
    val userId: String = "",
    val imageUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L
)

data class User(
    val id: String = "",
    val displayName: String = "",
    val searchName: String = "", // Lowercase for case-insensitive search
    val profilePhotoUrl: String = "",
    val username: String = "",
    val following: List<String> = emptyList()
)

enum class ActivityType {
    WALK, RUN;

    /** Returns "Walk" or "Run" for display in the UI. */
    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}
