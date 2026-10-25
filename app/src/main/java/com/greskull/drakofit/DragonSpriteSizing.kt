package com.greskull.drakofit

import android.graphics.Bitmap

/**
 * Tamaño constante del sprite del dragón en el mapa.
 *
 * PROBLEMA QUE RESUELVE
 * Los PNG de `res/drawable` no miden lo mismo: van de 114×128 (planta nivel 1)
 * a 655×353 (planta nivel 13). Como la capa usaba un `iconSize` fijo (0.30),
 * el dragón en el mapa crecía hasta 3,3 veces solo por subir de nivel.
 *
 * CÓMO LO RESUELVE
 * En vez de reescalar el bitmap (coste de memoria y pérdida de calidad), se
 * corrige el `iconSize`: se calcula un factor por sprite para que su lado
 * mayor equivalga siempre a [REFERENCE_MAX_SIDE_PX]. El resultado es el mismo
 * en cualquier densidad de pantalla, porque todos los PNG vienen de la misma
 * carpeta `res/drawable` y por tanto su densidad se cancela en el cociente.
 *
 * AJUSTE
 * - Dragón más pequeño: subir [REFERENCE_MAX_SIDE_PX] (p. ej. 260).
 * - Dragón más grande: bajarlo (p. ej. 160).
 * - Un sprite cuyo lado mayor mida exactamente [REFERENCE_MAX_SIDE_PX] se
 *   dibuja igual que antes de este cambio (iconSize base 0.30).
 */
object DragonSpriteSizing {

    /** Lado mayor de referencia, en píxeles. Ver nota de ajuste arriba. */
    const val REFERENCE_MAX_SIDE_PX = 260f

    /** Tamaño base que ya tenía la capa del dragón. */
    const val BASE_ICON_SIZE = 0.30f

    /**
     * Factor de normalización para este bitmap.
     * @return 1.0 si el sprite ya mide el lado de referencia.
     */
    fun factor(bitmap: Bitmap): Float {
        val maxSide = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        return REFERENCE_MAX_SIDE_PX / maxSide.toFloat()
    }

    /**
     * `iconSize` final: base × normalización × escala elegida por el usuario
     * (los botones +/- del mapa siguen funcionando igual).
     */
    fun iconSize(factor: Float, userScale: Float): Float =
        BASE_ICON_SIZE * factor * userScale
}
