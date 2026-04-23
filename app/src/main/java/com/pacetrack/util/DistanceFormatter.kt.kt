package com.pacetrack.util

/**
 * Distance formatting helpers used across PaceTrack screens.
 * Centralizing this logic keeps metres and kilometre labels consistent in
 * live tracking, summaries, history cards, and route detail views.
 */
object DistanceFormatter {

    /**
     * Converts metres to a readable string.
     * Under 1km → shows metres  e.g. "840 m"
     * Over 1km  → shows km      e.g. "3.42 km"
     */
    fun format(metres: Float): String {
        return if (metres < 1000f) {
            "${metres.toInt()} m"
        } else {
            val km = metres / 1000f
            "%.2f km".format(km)
        }
    }

    /** Returns just the raw km value for saving to Firestore. */
    fun toKm(metres: Float): Float = metres / 1000f
}
