package com.greskull.drakofit.tracking.domain

/**
 * Serialización de una ruta para guardarla en SharedPreferences.
 *
 * Formato: "lat,lon;lat,lon;..." con 5 decimales.
 * Cinco decimales son ~1,1 m de resolución, de sobra para redibujar la ruta,
 * y deja un punto en ~20 caracteres. Una ruta simplificada de 80 puntos ocupa
 * ~1,6 KB, así que las 5 rutas del historial caben de largo en preferencias.
 *
 * Se evita JSON a propósito: no hace falta ninguna librería y el formato se
 * puede leer a ojo mientras depuras con `adb shell run-as ... cat`.
 *
 * Kotlin puro, testeable en la JVM.
 */
object RouteCodec {

    private const val POINT_SEPARATOR = ";"
    private const val COORD_SEPARATOR = ","
    private const val DECIMALS = 5

    fun encode(points: List<RoutePoint>): String {
        if (points.isEmpty()) return ""
        val sb = StringBuilder(points.size * 20)
        points.forEachIndexed { index, p ->
            if (index > 0) sb.append(POINT_SEPARATOR)
            sb.append(format(p.lat))
                .append(COORD_SEPARATOR)
                .append(format(p.lon))
        }
        return sb.toString()
    }

    fun decode(raw: String?): List<RoutePoint> {
        if (raw.isNullOrBlank()) return emptyList()

        return raw.split(POINT_SEPARATOR).mapNotNull { chunk ->
            val parts = chunk.split(COORD_SEPARATOR)
            if (parts.size != 2) return@mapNotNull null
            val lat = parts[0].trim().toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            RoutePoint(lat, lon)
        }
    }

    private fun format(value: Double): String {
        // Locale.US fijo: con locale español, "%.5f" produciría "40,41680"
        // (coma decimal) y se rompería el separador de coordenadas.
        val s = String.format(java.util.Locale.US, "%.${DECIMALS}f", value)
        // trimEnd de ceros para no engordar el texto sin necesidad
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }
}
