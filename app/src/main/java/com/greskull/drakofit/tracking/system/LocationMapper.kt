package com.greskull.drakofit.tracking.system

import android.location.Location
import com.greskull.drakofit.tracking.domain.Fix

/**
 * Puente entre el `Location` de Android y el `Fix` del dominio.
 *
 * Este es el único punto donde el dominio "toca" Android, y lo hace en un
 * archivo aparte (`system/`) para que `domain/` siga siendo Kotlin puro y
 * testeable en la JVM.
 */
fun Location.toFix(): Fix = Fix(
    lat = latitude,
    lon = longitude,
    // elapsedRealtimeNanos es monotónico (no le afectan cambios de hora ni de
    // zona horaria del móvil). Se usa el timestamp del PROPIO fix en lugar de
    // leer el reloj ahora, porque location.time es hora de pared y puede venir
    // desfasada varios segundos.
    elapsedMs = elapsedRealtimeNanos / 1_000_000L,
    accuracyM = if (hasAccuracy()) accuracy else -1f,
    speedMps = if (hasSpeed()) speed.toDouble() else null,
)
