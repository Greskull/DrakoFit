package com.greskull.drakofit.tracking.persistence

import com.greskull.drakofit.tracking.domain.FilterSnapshot
import com.greskull.drakofit.tracking.domain.Status
import com.greskull.drakofit.tracking.domain.StopReason
import com.greskull.drakofit.tracking.domain.TrackPoint

/**
 * Resumen mínimo de una sesión, pensado para escribirse cada pocos segundos.
 *
 * Es un fichero de texto "clave=valor", una por línea: no depende de JSON ni
 * de ninguna librería, se puede leer a ojo mientras depuras en el móvil, y es
 * testeable en la JVM sin Android.
 *
 * NO guarda los puntos (esos van en el fichero binario de puntos).
 *
 * Los tiempos son de `SystemClock.elapsedRealtime()`, que es monotónico y
 * **de ámbito de sistema**: sigue siendo válido aunque el proceso muera y se
 * cree otro, así que una sesión interrumpida se puede reconstruir con la
 * misma base de tiempo. Un cambio de hora del móvil no lo altera.
 */
data class SessionSummary(
    /** Instante de arranque de la sesión (elapsedRealtime). */
    val startedAtElapsedMs: Long = 0L,
    /** Instante de la última escritura (elapsedRealtime), para saber la antigüedad. */
    val savedAtElapsedMs: Long = 0L,
    /** Reloj de sesión sin pausas, el que se muestra en el HUD. */
    val elapsedMs: Long = 0L,
    val activeMs: Long = 0L,
    val pausedMs: Long = 0L,
    val distanceM: Double = 0.0,
    val pointCount: Int = 0,
    val segmentCount: Int = 0,
    val status: Status = Status.IDLE,
    val stopReason: StopReason = StopReason.NONE,
    /** true si el usuario la tenía en pausa al morir el proceso. */
    val wasPaused: Boolean = false,
) {

    /** Hay algo recuperable: al menos un punto y distancia. */
    val isRecoverable: Boolean
        get() = pointCount > 0 && status != Status.IDLE

    fun serialize(): String = buildString {
        appendLine("v=1")
        appendLine("startedAt=$startedAtElapsedMs")
        appendLine("savedAt=$savedAtElapsedMs")
        appendLine("elapsed=$elapsedMs")
        appendLine("active=$activeMs")
        appendLine("paused=$pausedMs")
        appendLine("distance=$distanceM")
        appendLine("points=$pointCount")
        appendLine("segments=$segmentCount")
        appendLine("status=${status.name}")
        appendLine("stopReason=${stopReason.name}")
        appendLine("wasPaused=$wasPaused")
    }

    companion object {

        fun parse(text: String): SessionSummary? {
            if (text.isBlank()) return null
            val map = text.lineSequence()
                .mapNotNull { line ->
                    val i = line.indexOf('=')
                    if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
                }
                .toMap()

            return try {
                SessionSummary(
                    startedAtElapsedMs = map["startedAt"]?.toLongOrNull() ?: return null,
                    savedAtElapsedMs = map["savedAt"]?.toLongOrNull() ?: 0L,
                    elapsedMs = map["elapsed"]?.toLongOrNull() ?: 0L,
                    activeMs = map["active"]?.toLongOrNull() ?: 0L,
                    pausedMs = map["paused"]?.toLongOrNull() ?: 0L,
                    distanceM = map["distance"]?.toDoubleOrNull() ?: 0.0,
                    pointCount = map["points"]?.toIntOrNull() ?: 0,
                    segmentCount = map["segments"]?.toIntOrNull() ?: 0,
                    status = map["status"]?.let { runCatching { Status.valueOf(it) }.getOrNull() }
                        ?: Status.IDLE,
                    stopReason = map["stopReason"]?.let {
                        runCatching { StopReason.valueOf(it) }.getOrNull()
                    } ?: StopReason.NONE,
                    wasPaused = map["wasPaused"]?.toBooleanStrictOrNull() ?: false,
                )
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Reconstruye la foto del filtro para continuar la sesión.
         *
         * Se deja en estado PAUSED a propósito: **nunca se reanuda sola**. Si el
         * proceso murió y el usuario ha cambiado de sitio, reanudar sin más
         * metería un salto falso en la distancia. El usuario decide pulsando
         * "Continuar", y ese primer fix pasa por el chequeo de reanudar.
         */
        fun toFilterSnapshot(
            summary: SessionSummary,
            lastPoint: TrackPoint?,
        ): FilterSnapshot = FilterSnapshot(
            status = Status.PAUSED,
            stopReason = StopReason.NONE,
            sessionStartMs = summary.startedAtElapsedMs,
            graceUntilMs = 0L, // la gracia ya pasó hace rato
            lastSeenMs = lastPoint?.elapsedMs,
            lastAccepted = lastPoint,
            justResumed = false,
            suspicionPending = false,
            pausedStartedAtMs = summary.savedAtElapsedMs,
            segmentIndex = (summary.segmentCount - 1).coerceAtLeast(0),
            segmentBreakPending = false,
            distanceM = summary.distanceM,
            skippedDistanceM = 0.0,
            activeMs = summary.activeMs,
            pausedMs = summary.pausedMs,
            pointCount = summary.pointCount,
        )
    }
}
