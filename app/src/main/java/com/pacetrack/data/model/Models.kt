package com.pacetrack.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/*
 * Core data models shared by the whole PaceTrack app.
 * These classes describe what gets saved to Firebase and what the UI layer
 * expects to receive back when rendering runs, users, routes, and photos.
 */
/**
 * One completed activity session saved after tracking finishes.
 * The run stores summary metrics plus a compact encoded polyline so route
 * detail can rebuild the map without fetching a large point collection.
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
)

/**
 * One geographic sample captured during a tracking session.
 * These points are emitted live by the service and later reconstructed from
 * the encoded polyline when a saved route needs to be displayed again.
 */
data class RoutePoint(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val timestamp: Long = 0L
)

/**
 * Metadata for a photo attached to a run.
 * The actual image bytes live in Firebase Storage while this document holds
 * the download URL and the map position where the image was associated.
 */
data class Photo(
    val id: String = "",
    val runId: String = "",
    val userId: String = "",
    val imageUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L
)

/**
 * Minimal user profile data used by auth and social features.
 * Following is stored here so the Home feed can quickly determine which
 * public runs should be included for the signed-in user.
 */
data class User(
    val id: String = "",
    val displayName: String = "",
    val profilePhotoUrl: String = "",
    val username: String = "",
    val following: List<String> = emptyList()
)

/**
 * Supported activity types for the current MVP.
 * The enum flows from pre-run selection through tracking, saving, and detail
 * rendering so copy and icons stay aligned across the app.
 */
enum class ActivityType {
    WALK,
    RUN
}
