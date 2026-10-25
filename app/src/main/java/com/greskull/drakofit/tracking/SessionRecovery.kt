package com.greskull.drakofit.tracking

import android.content.Context
import android.os.SystemClock
import com.greskull.drakofit.DragonEngine
import com.greskull.drakofit.tracking.domain.SessionMath
import com.greskull.drakofit.tracking.domain.StopReason
import com.greskull.drakofit.tracking.domain.TrackPoint
import com.greskull.drakofit.tracking.persistence.SessionStore
import com.greskull.drakofit.tracking.persistence.SessionSummary

/**
 * FASE 3: recuperación de una sesión interrumpida.
 *
 * Si el proceso muere a mitad de sesión (ColorOS, MIUI, `am kill`, poca
 * memoria...), en disco queda el resumen y los puntos. Al abrir la app esto
 * se detecta y se le PREGUNTA al usuario:
 *
 *   • Continuar        -> el servicio retoma la sesión (en pausa, nunca sola).
 *   • Guardar y cerrar -> se guarda con el filtro de rutas largas de siempre.
 *
 * No se hace nada automáticamente: reanudar sola podría meter un salto falso
 * en la distancia si el usuario se ha movido mientras el proceso estaba muerto.
 */
object SessionRecovery {

    /** Sesión encontrada en disco. */
    data class Recovered(
        val summary: SessionSummary,
        val points: List<TrackPoint>,
        /** ms desde la última escritura (elapsedRealtime). */
        val savedAgoMs: Long,
    ) {
        val km: Float get() = SessionMath.kmFor(summary.distanceM)
        val steps: Int get() = SessionMath.stepsFor(km)
        val calories: Int get() = SessionMath.caloriesFor(km).toInt()
        val pace: String get() = SessionMath.paceFor(km, summary.elapsedMs)
        val formattedTime: String get() = SessionMath.formatTime(summary.elapsedMs)
    }

    private fun store(context: Context) =
        SessionStore.forContextFilesDir(context.filesDir)

    /**
     * Mira si hay una sesión sin cerrar.
     * @return null si no hay nada recuperable.
     */
    fun peek(context: Context): Recovered? {
        return try {
            val store = store(context)
            if (!store.hasPending()) return null

            val summary = store.readSummary() ?: return null
            if (!summary.isRecoverable) {
                // Resumen sin puntos: restos de un arranque fallido.
                store.clear()
                return null
            }

            Recovered(
                summary = summary,
                points = store.readPoints(),
                savedAgoMs = (SystemClock.elapsedRealtime() - summary.savedAtElapsedMs)
                    .coerceAtLeast(0L),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** ¿Hay una sesión activa ahora mismo en el servicio? */
    fun hasLiveSession(): Boolean = TrackingState.current().isActive

    /**
     * Opción "Continuar": el servicio retoma la sesión desde disco.
     * Se llama desde la UI; ver `TrackingService.restore`.
     */
    fun continuesInService(context: Context) {
        com.greskull.drakofit.TrackingService.restore(context)
    }

    /**
     * Opción "Guardar y cerrar": cierra la sesión con las mismas reglas de
     * siempre y borra los ficheros.
     *
     * @return true si la ruta pasó el filtro de rutas largas y se guardó.
     */
    fun saveAndClose(context: Context, engine: DragonEngine): Boolean {
        val recovered = peek(context) ?: return false

        val state = TrackingUiState(
            status = com.greskull.drakofit.tracking.domain.Status.FINISHED,
            stopReason = StopReason.USER,
            distanceM = recovered.summary.distanceM,
            elapsedMs = recovered.summary.elapsedMs,
            points = recovered.points,
            revision = recovered.points.size,
        )

        val saved = try {
            SessionFinalizer.finalize(context, engine, state)
        } catch (_: Exception) {
            false
        }

        // Misma racha que un finalizado normal.
        try {
            engine.registerActivity()
        } catch (_: Exception) {
        }

        store(context).clear()
        TrackingState.resetToIdle()

        return saved
    }

    /** Descarta la sesión sin guardar (por si el usuario quiere tirarla). */
    fun discard(context: Context) {
        store(context).clear()
        TrackingState.resetToIdle()
    }
}
