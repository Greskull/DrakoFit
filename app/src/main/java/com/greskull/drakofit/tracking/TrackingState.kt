package com.greskull.drakofit.tracking

import com.greskull.drakofit.tracking.domain.Status
import com.greskull.drakofit.tracking.domain.StopReason
import com.greskull.drakofit.tracking.domain.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de la sesión de tracking que comparten el servicio y la pantalla.
 *
 * Es la pieza que arregla el problema de fondo: el GPS y las métricas ya no
 * viven en el ciclo de vida de la Activity. Si `MapScreen` se destruye (rotación,
 * volver al inicio, proceso de la UI recreado) el [TrackingService] sigue
 * actualizando este estado, y al volver la pantalla se reconecta a él sin
 * perder ni un metro.
 *
 * El único que ESCRIBE es el servicio ([set]). La UI solo observa.
 */
data class TrackingUiState(
    val status: Status = Status.IDLE,
    val stopReason: StopReason = StopReason.NONE,
    /** Distancia acumulada, la misma que `accumulatedDistanceMeters` de antes. */
    val distanceM: Double = 0.0,
    /** Cronómetro de sesión sin pausas: el mismo `getElapsedTime()`. */
    val elapsedMs: Long = 0L,
    /** Puntos de la ruta, en orden. Se usa para dibujar la polilínea. */
    val points: List<TrackPoint> = emptyList(),
    /** Sube cada vez que hay un punto nuevo; sirve de clave para redibujar. */
    val revision: Int = 0,
) {
    /** Hay sesión en marcha (grabando o en pausa). */
    val isActive: Boolean
        get() = status == Status.TRACKING || status == Status.PAUSED

    /** La sesión ha terminado y la pantalla debe mostrar el resumen. */
    val isFinished: Boolean
        get() = status == Status.FINISHED

    val isPaused: Boolean
        get() = status == Status.PAUSED

    /** Terminó invalidada por GPS (equivale a `FinishReason.GPS_SUSPICIOUS`). */
    val isSuspicious: Boolean
        get() = status == Status.FINISHED && stopReason.isSuspicious

    // Accesos directos con las mismas fórmulas de siempre
    val km: Float
        get() = com.greskull.drakofit.tracking.domain.SessionMath.kmFor(distanceM)

    val steps: Int
        get() = com.greskull.drakofit.tracking.domain.SessionMath.stepsFor(km)

    val calories: Int
        get() = com.greskull.drakofit.tracking.domain.SessionMath.caloriesFor(km).toInt()

    val pace: String
        get() = com.greskull.drakofit.tracking.domain.SessionMath.paceFor(km, elapsedMs)

    val formattedTime: String
        get() = com.greskull.drakofit.tracking.domain.SessionMath.formatTime(elapsedMs)
}

/** Contenedor de proceso: sobrevive a la muerte de la Activity. */
object TrackingState {

    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()

    fun current(): TrackingUiState = _state.value

    /** Solo el servicio escribe aquí. */
    internal fun set(newState: TrackingUiState) {
        _state.value = newState
    }

    /**
     * Vuelve a IDLE. Se llama cuando la UI ya ha mostrado el resumen y el
     * usuario vuelve al inicio.
     */
    fun resetToIdle() {
        _state.value = TrackingUiState()
    }
}
