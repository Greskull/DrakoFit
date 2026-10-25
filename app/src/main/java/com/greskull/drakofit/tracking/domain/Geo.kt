package com.greskull.drakofit.tracking.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utilidades geográficas en Kotlin puro.
 *
 * IMPORTANTE: aquí no puede entrar nada de `android.location.Location` ni
 * `Location.distanceBetween`, porque los tests de la Fase 1 corren en la JVM
 * (`app/src/test`) y esas clases son stubs que lanzan `RuntimeException`.
 */
object Geo {

    /** Radio medio terrestre (IUGG), en metros. */
    private const val EARTH_RADIUS_M = 6_371_008.8

    /**
     * Distancia ortodrómica (haversine) en metros entre dos coordenadas.
     *
     * Error típico < 0,5 % frente a Vincenty/WGS84 para distancias cortas,
     * que es de sobra para acumular distancia de una ruta.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(lon2 - lon1)

        val sinDPhi = sin(dPhi / 2.0)
        val sinDLambda = sin(dLambda / 2.0)

        val a = sinDPhi * sinDPhi + cos(phi1) * cos(phi2) * sinDLambda * sinDLambda
        return 2.0 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /**
     * Velocidad media en m/s entre dos muestras separadas por [dtMs].
     * Protegido contra dt <= 0 (divide por 1 ms para no devolver infinito).
     */
    fun speedMps(distanceM: Double, dtMs: Long): Double {
        val safeDt = if (dtMs <= 0L) 1L else dtMs
        return distanceM / (safeDt / 1000.0)
    }

    /** Convierte m/s a km/h (para logs, UI y para razonar los umbrales). */
    fun mpsToKmh(mps: Double): Double = mps * 3.6
}
