package com.greskull.drakofit.tracking.domain

/**
 * Estado de la sesión.
 *
 * No hay estado `SUSPICIOUS` aparte: una sesión sospechosa es una sesión
 * FINISHED con [stopReason] distinto de [StopReason.USER]. La UI decide qué
 * hacer con eso (hoy: no guardar ruta, no sumar pasos, no dar XP).
 */
enum class Status { IDLE, TRACKING, PAUSED, FINISHED }

/** Por qué se cerró la sesión. */
enum class StopReason {

    /** Sigue abierta o nunca se abrió. */
    NONE,

    /** El usuario pulsó Finalizar. */
    USER,

    /**
     * El filtro de velocidad confirmó A→B→C: se superó [FilterConfig.confirmThresholdMps].
     * Equivale al `FinishReason.GPS_SUSPICIOUS` actual.
     */
    SUSPICIOUS_SPEED,

    /** Al reanudar, el salto desde el punto de pausa superó [FilterConfig.resumeMaxJumpM]. */
    SUSPICIOUS_RESUME,

    /** FASE 3: hueco de GPS con salto imposible al volver la señal. */
    SUSPICIOUS_GAP,
    ;

    /** true si es una finalización anómala que invalida la actividad. */
    val isSuspicious: Boolean get() = this != NONE && this != USER
}

/**
 * Motivo por el que un fix NO se ha tenido en cuenta (no terminal, salvo que
 * venga acompañado de [FixOutcome.SessionStopped]).
 */
enum class Rejection {
    NOT_STARTED,
    FINISHED,
    PAUSED,
    OUT_OF_ORDER,
    ACCURACY,
    NEGLIGIBLE,

    /** Primer tick sospechoso (B): se ignora y se espera confirmación en C. */
    SPEED_SUSPECT,

    /** Salto al reanudar. No terminal por sí solo: el terminal es SessionStopped. */
    RESUME_JUMP,
}

/**
 * Muestra del proveedor de ubicación, normalizada y sin dependencias de
 * Android (el servicio convierte `Location` -> `Fix`).
 *
 * @param elapsedMs instante en reloj monotónico (`SystemClock.elapsedRealtime()`),
 *   NO `System.currentTimeMillis()`. Un cambio de hora del móvil no altera
 *   ni el cronómetro ni el ritmo.
 * @param accuracyM radio de precisión horizontal; negativo si el proveedor no lo da.
 * @param speedMps velocidad del hardware; null si no está disponible.
 */
data class Fix(
    val lat: Double,
    val lon: Double,
    val elapsedMs: Long,
    val accuracyM: Float = 0f,
    val speedMps: Double? = null,
)

/**
 * Punto aceptado de la ruta.
 *
 * [segmentIndex] permite dibujar tramos separados (Fase 3). Hoy siempre valdrá
 * 0 salvo que se active `enableGapSplitting`.
 */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val elapsedMs: Long,
    val accuracyM: Float,
    val distanceM: Double,
    val segmentIndex: Int,
)

/** Resultado de pasar un fix por el filtro. */
sealed interface FixOutcome {

    val accepted: Boolean

    data class Accepted(
        val point: TrackPoint,
        val distanceDeltaM: Double,
        val newSegment: Boolean,
    ) : FixOutcome {
        override val accepted: Boolean get() = true
    }

    data class Rejected(val reason: Rejection) : FixOutcome {
        override val accepted: Boolean get() = false
    }

    /**
     * El fix ha FINALIZADO la sesión por GPS sospechoso.
     *
     * Este fix no suma ninguna métrica, y a partir de aquí todos los fixes se
     * rechazan con [Rejection.FINISHED]. El consumidor (hoy `MapScreen`, en la
     * Fase 2 el servicio) debe tratarlo como actividad inválida.
     */
    data class SessionStopped(val reason: StopReason) : FixOutcome {
        override val accepted: Boolean get() = false
    }
}

/**
 * Métricas de la sesión en un instante.
 *
 * @param totalMs cronómetro de sesión descontando pausas (lo que se muestra hoy).
 * @param activeMs tiempo en movimiento real. Hoy NO se usa para el ritmo (para
 *   mantener paridad exacta con `getPace()` actual se usa [totalMs]), pero es
 *   el dato correcto para la Fase 3 cuando los huecos de GPS empiecen a contar.
 * @param averagePaceSecPerKm ritmo en s/km; `null` si aún no hay 10 m.
 */
data class SessionMetrics(
    val status: Status,
    val stopReason: StopReason,
    val totalMs: Long,
    val activeMs: Long,
    val pausedMs: Long,
    val distanceM: Double,
    val averageSpeedMps: Double,
    val averagePaceSecPerKm: Double?,
    val segmentCount: Int,
    val pointCount: Int,
    val skippedDistanceM: Double,
    val suspicionPending: Boolean,
) {
    /** true si la sesión terminó invalidada por GPS. */
    val isSuspicious: Boolean get() = stopReason.isSuspicious
}

/**
 * Foto del estado completo, con tipos primitivos, para la Fase 3: si muere el
 * proceso se guarda esto y se reconstruye el filtro tal cual estaba.
 */
data class FilterSnapshot(
    val status: Status = Status.IDLE,
    val stopReason: StopReason = StopReason.NONE,
    val sessionStartMs: Long = 0L,
    val graceUntilMs: Long = 0L,
    val lastSeenMs: Long? = null,
    val lastAccepted: TrackPoint? = null,
    val justResumed: Boolean = false,
    val suspicionPending: Boolean = false,
    val pausedStartedAtMs: Long? = null,
    val segmentIndex: Int = -1,
    val segmentBreakPending: Boolean = false,
    val distanceM: Double = 0.0,
    val skippedDistanceM: Double = 0.0,
    val activeMs: Long = 0L,
    val pausedMs: Long = 0L,
    val pointCount: Int = 0,
)

/**
 * Núcleo del tracking: decide qué fixes cuentan, cuánta distancia suman, cuánto
 * tiempo activo llevan y **cuándo la sesión queda invalidada por GPS**.
 *
 * Kotlin puro: cero `android.*`, cero Compose, cero corrutinas, cero reloj
 * propio. Todo entra por parámetro y sale por retorno, así que se testea en la
 * JVM y lo puede usar indistintamente la pantalla (Fase 1b) o el servicio
 * (Fase 2).
 *
 * Reglas, en el mismo orden que `MapScreen`:
 *  1. Sin sesión / finalizada / en pausa => rechazo.
 *  2. Precisión > [FilterConfig.trustAccuracyM] => rechazo silencioso.
 *  3. Primer fix válido => origen de la ruta y arranque de la gracia.
 *  4. Al reanudar (un solo uso): salto > [FilterConfig.resumeMaxJumpM] =>
 *     **sesión finalizada como sospechosa**.
 *  5. Dentro de la gracia: no se evalúa velocidad, así que no puede finalizar
 *     la sesión. Si el salto es imposible, se re-ancla sin sumar distancia.
 *  6. Fuera de la gracia: A→B→C a [FilterConfig.confirmThresholdMps]. B solo
 *     levanta sospecha; C confirma y **finaliza la sesión como sospechosa**.
 */
class TrackingFilter private constructor(
    private val config: FilterConfig,
    snapshot: FilterSnapshot?,
) {

    constructor(config: FilterConfig = FilterConfig()) : this(config, null)

    var status: Status = Status.IDLE
        private set

    var stopReason: StopReason = StopReason.NONE
        private set

    /** Instante de arranque (reloj monotónico). */
    var sessionStartMs: Long = 0L
        private set

    /** Puntos aceptados hasta ahora. */
    var pointCount: Int = 0
        private set

    /** true si hay un tick B esperando confirmación en C. */
    var suspicionPending: Boolean = false
        private set

    private var graceUntilMs: Long = 0L
    private var lastSeenMs: Long? = null
    private var lastAccepted: TrackPoint? = null
    private var justResumed: Boolean = false
    private var pausedStartedAtMs: Long? = null
    private var segmentIndex: Int = -1
    private var segmentBreakPending: Boolean = false
    private var distanceM: Double = 0.0
    private var skippedDistanceM: Double = 0.0
    private var activeMs: Long = 0L
    private var pausedMs: Long = 0L

    init {
        snapshot?.let(::restoreFrom)
    }

    // ---------------------------------------------------------------- ciclo

    /** Inicia la sesión. Idempotente si ya está en marcha. */
    fun start(atMs: Long) {
        if (status == Status.TRACKING || status == Status.PAUSED) return
        status = Status.TRACKING
        stopReason = StopReason.NONE
        sessionStartMs = atMs
        // La gracia arranca con el primer fix válido, no con start(): igual que
        // MapScreen (trackingStartTimeMs se fija al primer fix que pasa accuracy).
        graceUntilMs = 0L
        lastSeenMs = null
        lastAccepted = null
        justResumed = false
        suspicionPending = false
        pausedStartedAtMs = null
        segmentIndex = -1
        segmentBreakPending = false
        distanceM = 0.0
        skippedDistanceM = 0.0
        activeMs = 0L
        pausedMs = 0L
        pointCount = 0
    }

    fun pause(atMs: Long) {
        if (status != Status.TRACKING) return
        status = Status.PAUSED
        pausedStartedAtMs = atMs
        suspicionPending = false
        lastSeenMs = maxOf(lastSeenMs ?: atMs, atMs)
    }

    /** Reanuda y arma el chequeo de salto de un solo uso. */
    fun resume(atMs: Long) {
        if (status != Status.PAUSED) return
        pausedStartedAtMs?.let { pausedMs += (atMs - it).coerceAtLeast(0) }
        pausedStartedAtMs = null
        status = Status.TRACKING
        justResumed = true
        suspicionPending = false
        lastSeenMs = maxOf(lastSeenMs ?: atMs, atMs)
    }

    /** Finaliza por decisión del usuario. */
    fun finish(atMs: Long) {
        pausedStartedAtMs?.let { pausedMs += (atMs - it).coerceAtLeast(0) }
        pausedStartedAtMs = null
        status = Status.FINISHED
        if (stopReason == StopReason.NONE) stopReason = StopReason.USER
        lastSeenMs = atMs
        suspicionPending = false
    }

    // ---------------------------------------------------------------- fixes

    fun onFix(fix: Fix): FixOutcome = when (status) {
        Status.IDLE -> FixOutcome.Rejected(Rejection.NOT_STARTED)
        Status.FINISHED -> FixOutcome.Rejected(Rejection.FINISHED)
        Status.PAUSED -> {
            lastSeenMs = maxOf(lastSeenMs ?: fix.elapsedMs, fix.elapsedMs)
            FixOutcome.Rejected(Rejection.PAUSED)
        }
        Status.TRACKING -> onTrackingFix(fix)
    }

    private fun onTrackingFix(fix: Fix): FixOutcome {
        // orden temporal estricto (protege contra timestamps raros del proveedor)
        lastSeenMs?.let {
            if (fix.elapsedMs <= it) return FixOutcome.Rejected(Rejection.OUT_OF_ORDER)
        }
        lastSeenMs = fix.elapsedMs

        // 2) filtro de precisión: descarta en silencio, NUNCA finaliza la sesión
        if (fix.accuracyM >= 0f && fix.accuracyM > config.trustAccuracyM) {
            return FixOutcome.Rejected(Rejection.ACCURACY)
        }

        val last = lastAccepted

        // 3) primer punto => origen
        if (last == null) {
            if (graceUntilMs == 0L) graceUntilMs = fix.elapsedMs + config.graceDurationMs
            segmentIndex = 0
            segmentBreakPending = false
            justResumed = false
            val point = TrackPoint(fix.lat, fix.lon, fix.elapsedMs, fix.accuracyM, 0.0, 0)
            lastAccepted = point
            pointCount = 1
            suspicionPending = false
            return FixOutcome.Accepted(point, distanceDeltaM = 0.0, newSegment = true)
        }

        val distFromA = Geo.haversineMeters(last.lat, last.lon, fix.lat, fix.lon)
        val dtMs = fix.elapsedMs - last.elapsedMs
        val speedFromA = Geo.speedMps(distFromA, dtMs)

        // 4) chequeo al reanudar: tope de DISTANCIA, de un solo uso.
        //    Ojo: se consume pase o no el chequeo, igual que `justResumed = false`.
        if (justResumed) {
            justResumed = false
            if (distFromA > config.resumeMaxJumpM) {
                skippedDistanceM += distFromA
                return stopAsSuspicious(StopReason.SUSPICIOUS_RESUME)
            }
        }

        // 4-bis) FASE 3: hueco de GPS. Si han pasado más de `gapThresholdMs`
        // sin fix, no se puede saber el camino recorrido, así que:
        //   - si la media del hueco es imposible (> umbral) => GPS desbocado
        //     o vehículo: la actividad se invalida.
        //   - si es plausible => se abre TRAMO NUEVO y no se suma la
        //     distancia del hueco (no se dibuja una recta sobre el hueco).
        // Nota: aquí hay tiempo real transcurrido, así que la velocidad es más
        // informativa que un tope de distancia fijo como el de reanudar.
        if (config.enableGapSplitting && dtMs > config.gapThresholdMs) {
            suspiciousPendingReset()
            return if (speedFromA > config.speedThresholdMps) {
                skippedDistanceM += distFromA
                stopAsSuspicious(StopReason.SUSPICIOUS_GAP)
            } else {
                skippedDistanceM += distFromA
                startSegment(fix)
            }
        }

        // 5) periodo de gracia: el filtro de velocidad NO puede finalizar nada.
        val inGrace = fix.elapsedMs <= graceUntilMs
        if (inGrace) {
            if (!config.accumulateDistanceDuringGrace) {
                return accept(fix, addDistance = 0.0, dtMs = dtMs, reanchorOnly = true)
            }
            // Si el salto es imposible, no se suman metros falsos Y tampoco se
            // añade el punto: se re-ancla en él, para no dejar una polilínea de
            // 5 puntos con 0 m si el usuario arranca la sesión ya en el coche.
            if (speedFromA > config.speedThresholdMps) {
                skippedDistanceM += distFromA
                reanchor(fix)
                return FixOutcome.Rejected(Rejection.SPEED_SUSPECT)
            }
            return accept(fix, addDistance = distFromA, dtMs = dtMs, reanchorOnly = false)
        }

        // 6) filtro A→B→C
        val threshold =
            if (suspicionPending) config.confirmThresholdMps else config.speedThresholdMps

        if (speedFromA > threshold) {
            if (!suspicionPending) {
                // B: primera sospecha. Se ignora y A sigue siendo A.
                suspicionPending = true
                return FixOutcome.Rejected(Rejection.SPEED_SUSPECT)
            }
            // C: confirmado => la actividad queda invalidada
            skippedDistanceM += distFromA
            return stopAsSuspicious(StopReason.SUSPICIOUS_SPEED)
        }

        // 7) parado según el propio hardware (opcional, desactivado por defecto)
        val stationary = config.stationarySpeedThresholdMps?.let { limit ->
            fix.speedMps != null && fix.speedMps < limit
        } ?: false
        if (stationary || distFromA < config.minDistanceDeltaM) {
            return FixOutcome.Rejected(Rejection.NEGLIGIBLE)
        }

        return accept(fix, addDistance = distFromA, dtMs = dtMs, reanchorOnly = false)
    }

    // ------------------------------------------------------------ internos

    private fun accept(
        fix: Fix,
        addDistance: Double,
        dtMs: Long,
        reanchorOnly: Boolean,
    ): FixOutcome {
        val opensSegment = segmentBreakPending
        if (opensSegment) {
            segmentIndex++
            segmentBreakPending = false
        }
        distanceM += addDistance
        if (!reanchorOnly && !opensSegment) {
            activeMs += dtMs.coerceIn(0L, config.gapThresholdMs)
        }
        val point = TrackPoint(
            lat = fix.lat,
            lon = fix.lon,
            elapsedMs = fix.elapsedMs,
            accuracyM = fix.accuracyM,
            distanceM = distanceM,
            segmentIndex = segmentIndex,
        )
        lastAccepted = point
        pointCount++
        suspicionPending = false
        return FixOutcome.Accepted(point, addDistance, opensSegment)
    }

    /**
     * Mueve el origen al fix actual sin sumar distancia, sin sumar tiempo y
     * sin crear punto. El siguiente fix válido se mide desde aquí.
     */
    private fun reanchor(fix: Fix) {
        lastAccepted = TrackPoint(
            lat = fix.lat,
            lon = fix.lon,
            elapsedMs = fix.elapsedMs,
            accuracyM = fix.accuracyM,
            distanceM = distanceM,
            segmentIndex = segmentIndex,
        )
    }

    /** Convierte la sesión en sospechosa: se acabó, sin sumar nada más. */
    private fun stopAsSuspicious(reason: StopReason): FixOutcome.SessionStopped {
        status = Status.FINISHED
        stopReason = reason
        suspicionPending = false
        justResumed = false
        return FixOutcome.SessionStopped(reason)
    }

    private fun suspiciousPendingReset() {
        suspicionPending = false
    }

    /** Abre tramo nuevo sin sumar distancia (solo con `enableGapSplitting`). */
    private fun startSegment(fix: Fix): FixOutcome {
        segmentIndex++
        segmentBreakPending = false
        val point = TrackPoint(
            lat = fix.lat,
            lon = fix.lon,
            elapsedMs = fix.elapsedMs,
            accuracyM = fix.accuracyM,
            distanceM = distanceM,
            segmentIndex = segmentIndex,
        )
        lastAccepted = point
        pointCount++
        suspicionPending = false
        return FixOutcome.Accepted(point, 0.0, newSegment = true)
    }

    // ------------------------------------------------------------ métricas

    /** ms desde el último fix recibido; null si aún no ha llegado ninguno. */
    fun msSinceLastFix(nowMs: Long): Long? = lastSeenMs?.let { (nowMs - it).coerceAtLeast(0) }

    /**
     * Métricas en el instante [nowMs]. Se pasa desde fuera para poder refrescar
     * la UI entre fix y fix (el cronómetro avanza cada segundo aunque el GPS
     * tarde 3,5 s en dar la siguiente posición).
     */
    fun metrics(nowMs: Long = lastSeenMs ?: sessionStartMs): SessionMetrics {
        val currentlyPaused = pausedStartedAtMs?.let { (nowMs - it).coerceAtLeast(0) } ?: 0L
        val totalMs =
            if (status == Status.IDLE) 0L
            else (nowMs - sessionStartMs - pausedMs - currentlyPaused).coerceAtLeast(0L)

        val averageSpeed = if (totalMs > 0L) distanceM / (totalMs / 1000.0) else 0.0

        // Paridad exacta con getPace() actual: el ritmo se calcula sobre el
        // cronómetro de sesión (sin pausas). Para usar tiempo en movimiento
        // real basta con cambiar totalMs por activeMs en esta línea.
        val pace = if (totalMs > 0L && distanceM >= config.minPaceDistanceM) {
            (totalMs / 1000.0) / (distanceM / 1000.0)
        } else {
            null
        }

        return SessionMetrics(
            status = status,
            stopReason = stopReason,
            totalMs = totalMs,
            activeMs = activeMs,
            pausedMs = pausedMs + currentlyPaused,
            distanceM = distanceM,
            averageSpeedMps = averageSpeed,
            averagePaceSecPerKm = pace,
            segmentCount = (segmentIndex + 1).coerceAtLeast(0),
            pointCount = pointCount,
            skippedDistanceM = skippedDistanceM,
            suspicionPending = suspicionPending,
        )
    }

    /** true si hay algo que guardar (al menos 2 puntos). */
    fun hasUsableRoute(): Boolean = pointCount >= 2

    // ----------------------------------------------------- persistencia (F3)

    fun snapshot(): FilterSnapshot = FilterSnapshot(
        status = status,
        stopReason = stopReason,
        sessionStartMs = sessionStartMs,
        graceUntilMs = graceUntilMs,
        lastSeenMs = lastSeenMs,
        lastAccepted = lastAccepted,
        justResumed = justResumed,
        suspicionPending = suspicionPending,
        pausedStartedAtMs = pausedStartedAtMs,
        segmentIndex = segmentIndex,
        segmentBreakPending = segmentBreakPending,
        distanceM = distanceM,
        skippedDistanceM = skippedDistanceM,
        activeMs = activeMs,
        pausedMs = pausedMs,
        pointCount = pointCount,
    )

    private fun restoreFrom(s: FilterSnapshot) {
        status = s.status
        stopReason = s.stopReason
        sessionStartMs = s.sessionStartMs
        graceUntilMs = s.graceUntilMs
        lastSeenMs = s.lastSeenMs
        lastAccepted = s.lastAccepted
        justResumed = s.justResumed
        suspicionPending = s.suspicionPending
        pausedStartedAtMs = s.pausedStartedAtMs
        segmentIndex = s.segmentIndex
        segmentBreakPending = s.segmentBreakPending
        distanceM = s.distanceM
        skippedDistanceM = s.skippedDistanceM
        activeMs = s.activeMs
        pausedMs = s.pausedMs
        pointCount = s.pointCount
    }

    companion object {
        /** Reconstruye un filtro desde una foto guardada (Fase 3). */
        fun restore(config: FilterConfig, snapshot: FilterSnapshot): TrackingFilter =
            TrackingFilter(config, snapshot)
    }
}
