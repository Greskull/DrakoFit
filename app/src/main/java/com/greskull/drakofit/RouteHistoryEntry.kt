package com.greskull.drakofit
data class RouteHistoryEntry(

    val id: Long = System.currentTimeMillis(),

    val timestamp: Long = System.currentTimeMillis(),

    val durationSeconds: Long,

    val distanceKm: Float,

    val steps: Int,

    val calories: Int,

    val pace: String,

    val xpEarned: Int,

    /**
     * FASE 4: recorrido simplificado (RDP) para dibujar el mapa y la miniatura.
     *
     * Lista VACIA = ruta antigua, guardada antes de la Fase 4: solo tiene
     * metricas. Se muestra en el historial sin mapa, sin romper nada.
     *
     * Tiene valor por defecto para que todas las llamadas que ya existian
     * (y las de los tests) sigan compilando.
     */
    val points: List<com.greskull.drakofit.tracking.domain.RoutePoint> = emptyList()
) {

    /** true si hay recorrido que dibujar. */
    val hasTrack: Boolean get() = points.size >= 2
}