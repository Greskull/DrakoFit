package com.example.drakofit

import android.content.Context

class RouteHistoryStorage(context: Context) {

    private val prefs = context.getSharedPreferences(
        "drakofit_route_history",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val MAX_ROUTES = 5
    }

    // =====================================================
    // GUARDA UNA NUEVA RUTA
    // =====================================================

    fun saveRoute(route: RouteHistoryEntry): Boolean {
        // Ignorar rutas demasiado cortas
        if (
            route.steps < 500 ||
            route.durationSeconds < 250
        ) {
            return false
        }

        val routes = loadRoutes().toMutableList()

        // Añadimos la nueva al principio
        routes.add(0, route)

        // Mantener máximo 5 rutas
        while (routes.size > MAX_ROUTES) {
            routes.removeAt(routes.lastIndex)
        }

        val editor = prefs.edit()

        editor.putInt("route_count", routes.size)

        routes.forEachIndexed { index, item ->

            editor.putLong("timestamp_$index", item.timestamp)

            editor.putLong("duration_$index", item.durationSeconds)

            editor.putFloat("distance_$index", item.distanceKm)

            editor.putInt("steps_$index", item.steps)

            editor.putInt("calories_$index", item.calories)

            editor.putString("pace_$index", item.pace)

            editor.putInt("xp_$index", item.xpEarned)
        }

        editor.apply()

        return true
    }

    fun getRecentRoutes(): List<RouteHistoryEntry> {
        return loadRoutes()
    }

    // =====================================================
    // CARGAR HISTORIAL
    // =====================================================

    fun loadRoutes(): List<RouteHistoryEntry> {

        val count = prefs.getInt("route_count", 0)

        val routes = mutableListOf<RouteHistoryEntry>()

        for (i in 0 until count) {

            routes.add(

                RouteHistoryEntry(

                    timestamp = prefs.getLong(
                        "timestamp_$i",
                        0L
                    ),

                    durationSeconds = prefs.getLong("duration_$i", 0L),

                    distanceKm = prefs.getFloat(
                        "distance_$i",
                        0f
                    ),

                    steps = prefs.getInt(
                        "steps_$i",
                        0
                    ),

                    calories = prefs.getInt(
                        "calories_$i",
                        0
                    ),

                    pace = prefs.getString(
                        "pace_$i",
                        "--"
                    ) ?: "--",

                    xpEarned = prefs.getInt(
                        "xp_$i",
                        0
                    )
                )
            )
        }

        return routes
    }

    fun clearHistory() {

        prefs.edit().clear().apply()
    }
}