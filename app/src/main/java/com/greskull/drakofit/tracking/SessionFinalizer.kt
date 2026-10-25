package com.greskull.drakofit.tracking

import android.content.Context
import com.greskull.drakofit.DragonEngine
import com.greskull.drakofit.RouteHistoryEntry
import com.greskull.drakofit.RouteHistoryStorage
import com.greskull.drakofit.tracking.domain.RoutePoint
import com.greskull.drakofit.tracking.domain.RouteSimplifier
import com.greskull.drakofit.tracking.domain.SessionMath

/**
 * Cierre de sesión y guardado de la ruta.
 *
 * Reproduce EXACTAMENTE el orden y las condiciones que había repartidos entre
 * el botón "Finalizar" de `MapScreen` y su `LaunchedEffect(finishReason)`:
 *
 *   1. si la actividad es inválida (GPS sospechoso) → no se guarda nada,
 *      no se suman pasos, no se da XP  (igual que `FinishReason.GPS_SUSPICIOUS`)
 *   2. `engine.addSteps(steps)` y `engine.addDistance(km)`
 *   3. `RouteHistoryStorage.saveRoute(route)`  → filtro de rutas largas
 *   4. `engine.onValidActivityCompleted()` solo si saveRoute devolvió true
 *
 * Está en un objeto aparte porque ahora hay dos caminos que pueden cerrar la
 * sesión: el botón "Finalizar" y el cierre desde el gestor de apps
 * (`onTaskRemoved`). Ambos deben guardar lo mismo, y solo puede guardar uno.
 */
object SessionFinalizer {

    /**
     * @return true si la ruta pasó el filtro de rutas largas y quedó guardada.
     */
    fun finalize(context: Context, engine: DragonEngine, state: TrackingUiState): Boolean {

        // 🚫 Actividad anulada por GPS sospechoso: nada de nada.
        if (state.isSuspicious) return false

        val km = state.km
        val elapsedMs = state.elapsedMs
        val sessionSteps = SessionMath.stepsFor(km)

        // FASE 4: el recorrido se simplifica con Ramer-Douglas-Peucker antes
        // de guardarlo. Una sesión de 1 h (~1000 puntos a 3,5 s por fix) queda
        // en unas decenas, con la misma forma y sin perder longitud.
        // Las METRICAS no se tocan: se siguen calculando con los datos reales
        // de la sesión, no con la ruta simplificada.
        val rutaSimplificada = RouteSimplifier.simplify(
            points = state.points.map { RoutePoint(it.lat, it.lon) },
            epsilonM = RouteSimplifier.DEFAULT_EPSILON_M,
        )

        val route = RouteHistoryEntry(
            distanceKm = km,
            durationSeconds = elapsedMs / 1000L,
            steps = sessionSteps,
            calories = SessionMath.caloriesFor(km).toInt(),
            pace = SessionMath.paceFor(km, elapsedMs),
            xpEarned = sessionSteps / 100,
            points = rutaSimplificada,
        )

        // 2) Mismo orden que el onClick de "Finalizar"
        engine.addSteps(sessionSteps)
        engine.addDistance(km)

        // 3) Filtro de rutas largas (steps < 500 || duration < 250 s => descarta)
        val validRoute = RouteHistoryStorage(context).saveRoute(route)

        // 🎁 Solo las actividades completamente válidas generan recompensas
        if (validRoute) {
            engine.onValidActivityCompleted()
        }

        return validRoute
    }
}
