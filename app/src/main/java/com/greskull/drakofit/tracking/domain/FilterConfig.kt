package com.greskull.drakofit.tracking.domain

/**
 * Umbrales del filtro. Todos inyectables: se pueden calibrar en dispositivo
 * sin tocar la lógica, y los tests pueden usar valores pequeños.
 *
 * Los valores por defecto son EXACTAMENTE los de `MapScreen.kt` (main), para
 * que la Fase 1b no cambie el comportamiento en producción.
 */
data class FilterConfig(

    /**
     * Precisión horizontal máxima (m) para fiarse de un fix.
     * Equivale a `MIN_ACCURACY_TO_TRUST = 25f` de MapScreen.
     * Un fix con precisión desconocida (valor negativo) se acepta, igual que
     * hoy: `loc.hasAccuracy() && loc.accuracy > MIN`.
     */
    val trustAccuracyM: Float = 25f,

    /**
     * Velocidad máxima creíble entre A y B (m/s). 15 m/s = 54 km/h.
     * Equivale a `SPEED_THRESHOLD_MS`.
     */
    val speedThresholdMps: Double = 15.0,

    /**
     * Velocidad para CONFIRMAR en C. Hoy es igual que [speedThresholdMps]
     * (en una versión anterior era el doble y el filtro era inútil: entre 54 y
     * 108 km/h no saltaba nunca).
     */
    val confirmThresholdMps: Double = 15.0,

    /**
     * Periodo de gracia desde el primer fix válido (ms). Equivale a
     * `FILTER_GRACE_PERIOD_MS = 5000`. Existe por los terminales de gama baja
     * (Redmi) que al arrancar dan fixes erráticos: durante la gracia el filtro
     * de velocidad NO puede finalizar la sesión.
     */
    val graceDurationMs: Long = 5_000L,

    /**
     * Distancia máxima (m) entre el punto de pausa y el primer fix al
     * reanudar. Equivale a `MAX_RESUME_JUMP_METERS = 150f`.
     *
     * Es un tope de DISTANCIA puro, no de velocidad: el tiempo en pausa puede
     * ser un segundo o media hora y no dice nada fiable.
     */
    val resumeMaxJumpM: Double = 150.0,

    /**
     * Durante la gracia, ¿se acumula distancia?
     *
     * `true` mantiene el comportamiento actual de MapScreen (la distancia se
     * acumula siempre que haya punto anterior, la gracia solo desactiva el
     * filtro de velocidad).
     *
     * Pero con un matiz: durante la gracia un salto imposible (más rápido que
     * [speedThresholdMps]) se acepta como nuevo origen con 0 m sumados, en vez
     * de sumar metros falsos. No finaliza la sesión (sigue siendo gracia),
     * simplemente no regala distancia. Si arrancas la sesión ya dentro del
     * coche, esto evita los ~85 m falsos de los primeros 5 s.
     */
    val accumulateDistanceDuringGrace: Boolean = true,

    /**
     * Distancia mínima (m) para considerar que hay desplazamiento.
     * Con 0.0 se acepta cualquier distancia positiva, que es lo que hace hoy
     * MapScreen. Subirlo a ~1.5 m elimina la distancia falsa por temblor del
     * GPS con el usuario parado: es la mejora más rentable que se puede
     * activar en la Fase 1b tras medir.
     */
    val minDistanceDeltaM: Double = 0.0,

    /**
     * Si viene informada la velocidad del hardware y es menor que esto, el
     * usuario está parado y no se acumula distancia. `null` = desactivado
     * (comportamiento actual).
     */
    val stationarySpeedThresholdMps: Double? = null,

    /**
     * FASE 3, no activar todavía. Parte la ruta cuando pasan más de
     * [gapThresholdMs] sin fix, en lugar de unir los tramos con una recta.
     */
    val enableGapSplitting: Boolean = false,

    /** FASE 3: umbral de hueco sin GPS. */
    val gapThresholdMs: Long = 60_000L,

    /** Distancia mínima para calcular ritmo, igual que el `km < 0.01f` actual. */
    val minPaceDistanceM: Double = 10.0,
)
