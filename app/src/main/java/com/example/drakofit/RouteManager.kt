package com.example.drakofit

import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

object RouteManager {

    // =====================================================
    // 🧠 STATE
    // =====================================================

    private val points = mutableListOf<LatLng>()
    private var routeSource: GeoJsonSource? = null
    private var lastPoint: LatLng? = null
    private var onDistanceUpdate: ((Float) -> Unit)? = null
    private data class TimePoint(
        val time: Long,
        val distance: Float
    )
    private fun updatePaceWindow() {

        val now = System.currentTimeMillis()

        paceWindow.add(
            TimePoint(now, totalDistance)
        )

        // limpiar datos antiguos (25s)
        paceWindow.removeAll {
            now - it.time > WINDOW_MS
        }
    }

    private val paceWindow = mutableListOf<TimePoint>()
    private const val WINDOW_MS = 25_000L
    fun getStablePace(): String {

        if (paceWindow.size < 2) return "--:--"

        val first = paceWindow.first()
        val last = paceWindow.last()

        val distanceDeltaKm = (last.distance - first.distance) / 1000f
        val timeDeltaMin = (last.time - first.time) / 60000f

        if (distanceDeltaKm < 0.01f) return "--:--"

        val pace = timeDeltaMin / distanceDeltaKm

        val min = pace.toInt()
        val sec = ((pace - min) * 60).toInt()

        return "%d:%02d".format(min, sec)
    }

    fun bindDistanceCallback(callback: (Float) -> Unit) {
        onDistanceUpdate = callback
    }
    private var totalDistance = 0f

    // =====================================================
    // 🧠 KALMAN FILTER (ULTRA LIGHT)
    // =====================================================

    private class KalmanLatLngFilter(
        private val q: Float = 0.00001f,
        private val r: Float = 0.01f
    ) {
        private var lat: Float? = null
        private var lon: Float? = null
        private var variance = -1f

        fun filter(newLat: Float, newLon: Float): Pair<Float, Float> {

            if (variance < 0) {
                lat = newLat
                lon = newLon
                variance = r
                return newLat to newLon
            }

            variance += q

            val k = variance / (variance + r)

            lat = lat!! + k * (newLat - lat!!)
            lon = lon!! + k * (newLon - lon!!)

            variance = (1 - k) * variance

            return lat!! to lon!!
        }
    }

    private val kalman = KalmanLatLngFilter()

    // =====================================================
    // 🧩 BIND SOURCE
    // =====================================================

    fun bindSource(source: GeoJsonSource) {
        routeSource = source
    }

    // =====================================================
    // 🧠 ADD POINT (GPS ENTRY POINT)
    // =====================================================

    fun addPoint(newPoint: LatLng, accuracy: Float) {

        // 🚨 filtro precisión GPS
        if (accuracy > 18f) return

        // 🧠 KALMAN FILTER (reduce jitter urbano)
        val filtered = kalman.filter(
            newPoint.latitude.toFloat(),
            newPoint.longitude.toFloat()
        )

        val kalmanPoint = LatLng(
            filtered.first.toDouble(),
            filtered.second.toDouble()
        )

        // 🚨 anti saltos + smoothing doble capa
        lastPoint?.let { last ->

            val results = FloatArray(1)

            android.location.Location.distanceBetween(
                last.latitude, last.longitude,
                kalmanPoint.latitude, kalmanPoint.longitude,
                results
            )

            val distance = results[0]

// 🚫 filtros anti-ruido GPS
            if (distance < 1f) return
            if (distance > 80f) return

// 📏 actualizar distancia total
            totalDistance += distance

            val now = System.currentTimeMillis()

// 🧠 actualizar ventana de ritmo
            paceWindow.add(
                TimePoint(now, totalDistance)
            )

// limpiar datos antiguos
            paceWindow.removeAll {
                now - it.time > WINDOW_MS
            }

// 📡 NOTIFICAR SIEMPRE (CLAVE DEL BUG)
            onDistanceUpdate?.invoke(totalDistance)
            updatePaceWindow()

// limpiar datos antiguos
            paceWindow.removeAll {
                now - it.time > WINDOW_MS
            }
            onDistanceUpdate?.invoke(totalDistance)
            updatePaceWindow()

            // 🔥 suavizado exponencial (refuerzo visual)
            val alpha = 0.30f

            val smooth = LatLng(
                last.latitude + (kalmanPoint.latitude - last.latitude) * alpha,
                last.longitude + (kalmanPoint.longitude - last.longitude) * alpha
            )

            points.add(smooth)
            lastPoint = smooth

        } ?: run {
            points.add(kalmanPoint)
            lastPoint = kalmanPoint
        }

        redraw()
    }

    // =====================================================
    // 🗺 RENDER
    // =====================================================

    fun redraw() {

        val source = routeSource ?: return

        val features = points.map {
            Feature.fromGeometry(
                Point.fromLngLat(it.longitude, it.latitude)
            )
        }

        source.setGeoJson(
            FeatureCollection.fromFeatures(features)
        )
    }

    // =====================================================
    // 🔁 RESTORE AFTER BACKGROUND
    // =====================================================

    fun restore() {
        redraw()
    }
}