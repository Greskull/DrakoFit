package com.greskull.drakofit.tracking.domain

/**
 * Fórmulas de la sesión, copiadas LITERALMENTE de las funciones locales que
 * había en `MapScreen` (`getCalories`, `getSteps`, `getPace`, `formatTime`).
 *
 * No se ha cambiado ni un coeficiente: 65 kcal/km, 1300 pasos/km, el mismo
 * formato de ritmo y el mismo "--:--" por debajo de 10 m.
 *
 * Viven aquí porque ahora hay dos consumidores que deben dar EXACTAMENTE el
 * mismo número: la pantalla y la notificación del servicio. Si estuvieran
 * duplicadas, en la notificación y en el HUD saldrían valores distintos.
 */
object SessionMath {

    /** Equivale a `getCalories(km) = km * 65f`. */
    fun caloriesFor(km: Float): Float = km * 65f

    /** Equivale a `getSteps(km): Int = (km * 1300).toInt()`. */
    fun stepsFor(km: Float): Int = (km * 1300).toInt()

    /** Equivale a `formatTime(ms)`. */
    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    /** Equivale a `getPace(km, ms)`. Mismo "--:--" y mismo redondeo. */
    fun paceFor(km: Float, ms: Long): String {
        if (km < 0.01f) return "--:--"

        val pace = (ms / 60000f) / km

        val min = pace.toInt()
        val sec = ((pace - min) * 60).toInt()

        return "%d:%02d".format(min, sec)
    }

    /** Km a partir de metros, igual que `accumulatedDistanceMeters / 1000f`. */
    fun kmFor(distanceM: Double): Float = (distanceM / 1000.0).toFloat()
}
