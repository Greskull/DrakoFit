package com.greskull.drakofit.tracking.domain

/**
 * Un punto de la ruta tal y como se guarda en el historial.
 *
 * No se usa `org.maplibre.geojson.Point` a propósito: el historial se guarda
 * en SharedPreferences y no queremos que el modelo de datos dependa de la
 * librería del mapa (ni de Android, para poder testearlo en la JVM).
 */
data class RoutePoint(
    val lat: Double,
    val lon: Double,
)
