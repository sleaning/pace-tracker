package com.pacetrack.util

/**
 * Pace formatting helpers shared across tracking, history, and feed screens.
 * Keeping these conversions in one place ensures time and pace strings are
 * rendered consistently anywhere the app shows run metrics.
 */
object PaceFormatter {

    /**
     * Converts seconds-per-km into a readable mm:ss /km string.
     * e.g. 312f → "5:12 /km"
     * Returns "--:--" if pace is 0 (no data yet).
     */
    fun format(secondsPerKm: Float): String {
        if (secondsPerKm <= 0f) return "--:--"
        val totalSeconds = secondsPerKm.toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d /km".format(minutes, seconds)
    }

    /**
     * Formats elapsed milliseconds as hh:mm:ss or mm:ss depending on length.
     * e.g. 3_810_000ms → "1:03:30"
     *      312_000ms   → "5:12"
     */
    fun formatElapsed(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
