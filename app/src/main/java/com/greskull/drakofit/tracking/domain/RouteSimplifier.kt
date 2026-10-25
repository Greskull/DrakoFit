package com.greskull.drakofit.tracking.domain

import kotlin.math.abs
import kotlin.math.cos

/**
 * Simplificación de rutas con Ramer–Douglas–Peucker.
 *
 * Para qué sirve: una sesión de 60 minutos a 3,5 s por fix genera ~1000
 * puntos. Guardarlos todos en SharedPreferences es absurdo: la ruta ocupa
 * espacio, se lee y se escribe entera en cada guardado y se dibuja más lenta.
 * Con epsilon de 5 m, una caminata típica queda en unas decenas de puntos y la
 * forma del recorrido es indistinguible.
 *
 * Kotlin puro (sin android.*), así que se testea en la JVM.
 *
 * La distancia se calcula en METROS con una proyección equirectangular local
 * alrededor del primer punto. A escala de una ruta (decenas de km) el error
 * frente a haversine es despreciable, y es mucho más barato porque RDP hace
 * O(n log n) llamadas a esta función.
 */
object RouteSimplifier {

    /** Epsilon por defecto: 5 m. Un desvío menor de 5 m no se nota en pantalla. */
    const val DEFAULT_EPSILON_M = 5.0

    /**
     * @param points ruta original, en orden.
     * @param epsilonM tolerancia en metros. A mayor valor, menos puntos.
     * @return subconjunto de [points], conservando el primero y el último.
     */
    fun simplify(
        points: List<RoutePoint>,
        epsilonM: Double = DEFAULT_EPSILON_M,
    ): List<RoutePoint> {

        if (points.size <= 2) return points
        if (epsilonM <= 0.0) return points

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        // Pila en vez de recursión: una ruta larga puede anidar cientos de
        // niveles y no queremos arriesgar un StackOverflow.
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, points.size - 1))

        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast()

            var maxDistance = 0.0
            var index = -1

            for (i in first + 1 until last) {
                val d = perpendicularDistanceM(points[i], points[first], points[last])
                if (d > maxDistance) {
                    maxDistance = d
                    index = i
                }
            }

            if (index != -1 && maxDistance > epsilonM) {
                keep[index] = true
                stack.addLast(intArrayOf(first, index))
                stack.addLast(intArrayOf(index, last))
            }
        }

        val result = ArrayList<RoutePoint>(points.size)
        for (i in points.indices) if (keep[i]) result.add(points[i])

        return result
    }

    /**
     * Distancia (m) del punto [p] a la recta que une [a] y [b].
     * Si [a] == [b], devuelve la distancia de [p] a [a].
     */
    private fun perpendicularDistanceM(p: RoutePoint, a: RoutePoint, b: RoutePoint): Double {
        // Proyección local: 1 grado de latitud ~111320 m; de longitud, escalado
        // por cos(lat). Es una aproximación plana válida a escala de ruta.
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(a.lat))

        val px = (p.lon - a.lon) * mPerDegLon
        val py = (p.lat - a.lat) * mPerDegLat

        val bx = (b.lon - a.lon) * mPerDegLon
        val by = (b.lat - a.lat) * mPerDegLat

        val segmentLengthSq = bx * bx + by * by

        // Caso degenerado: a y b son el mismo punto
        if (segmentLengthSq < 1e-9) return kotlin.math.sqrt(px * px + py * py)

        // Proyección escalar de p sobre el segmento a->b
        var t = (px * bx + py * by) / segmentLengthSq
        t = t.coerceIn(0.0, 1.0)

        val projX = t * bx
        val projY = t * by

        val dx = px - projX
        val dy = py - projY

        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Longitud total (m) de una ruta, útil para comparar antes/después. */
    fun pathLengthM(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += Geo.haversineMeters(
                points[i - 1].lat, points[i - 1].lon,
                points[i].lat, points[i].lon,
            )
        }
        return total
    }
}
