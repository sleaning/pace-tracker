package com.pacetrack.util

import com.google.android.gms.maps.model.LatLng

/**
 * PolylineEncoder
 *
 * Encodes and decodes a list of LatLng coordinates using Google's polyline
 * encoding algorithm. This lets us store an entire GPS route as a single
 * compact string in the Firestore run document instead of storing each
 * point as a separate document.
 *
 * Example: 100 GPS points → one string ~200 characters long.
 */
object PolylineEncoder {

    /**
     * Encodes a list of LatLng into a Google encoded polyline string.
     * Called when the user stops a run, before saving to Firestore.
     */
    fun encode(points: List<LatLng>): String {
        val result = StringBuilder()
        var prevLat = 0
        var prevLng = 0

        for (point in points) {
            val lat = (point.latitude * 1e5).toInt()
            val lng = (point.longitude * 1e5).toInt()
            encodeValue(lat - prevLat, result)
            encodeValue(lng - prevLng, result)
            prevLat = lat
            prevLng = lng
        }

        return result.toString()
    }

    /**
     * Decodes a Google encoded polyline string back into a list of LatLng.
     * Called when loading a run from Firestore to redraw the route on the map.
     */
    fun decode(encoded: String): List<LatLng> {
        val points = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        // Google polyline strings store delta-encoded latitude/longitude
        // values, so decoding rebuilds each point relative to the previous one.
        while (index < encoded.length) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            points.add(LatLng(lat / 1e5, lng / 1e5))
        }

        return points
    }

    /**
     * Encodes one signed coordinate delta into the polyline character stream.
     * Google polyline format packs five bits at a time, which is why the loop
     * keeps emitting characters until the remaining value fits in one chunk.
     */
    private fun encodeValue(value: Int, result: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            result.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        result.append((v + 63).toChar())
    }
}
