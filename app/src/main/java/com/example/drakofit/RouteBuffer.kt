package com.example.drakofit

import org.maplibre.android.geometry.LatLng

object RouteBuffer {

    val points = mutableListOf<LatLng>()

    fun addPoint(point: LatLng) {

        if (points.isNotEmpty()) {
            val last = points.last()

            val distance = calculateDistance(last, point)

            // filtro anti saltos GPS (teleport bug)
            if (distance > 80) return
        }

        points.add(point)
    }

    fun clear() {
        points.clear()
    }

    private fun calculateDistance(a: LatLng, b: LatLng): Double {
        val results = FloatArray(1)

        android.location.Location.distanceBetween(
            a.latitude, a.longitude,
            b.latitude, b.longitude,
            results
        )

        return results[0].toDouble()
    }
}