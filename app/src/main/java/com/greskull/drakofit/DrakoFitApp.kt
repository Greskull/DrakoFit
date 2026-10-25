package com.greskull.drakofit

import android.app.Application

/**
 * FASE 2: el `DragonEngine` pasa a vivir en la Application.
 *
 * Antes lo creaba `MainActivity`, así que solo existía mientras hubiera una
 * Activity viva. Ahora `TrackingService` también necesita guardar la ruta y
 * sumar pasos/XP cuando la app muere (cierre desde el gestor de tareas), y
 * tiene que ser EXACTAMENTE el mismo motor: si hubiera dos instancias,
 * escribirían el mismo SharedPreferences y se pisarían el estado.
 *
 * Sigue siendo una única instancia, con el mismo ciclo de vida de siempre
 * (una por proceso) y el mismo contexto de creación.
 */
class DrakoFitApp : Application() {

    lateinit var engine: DragonEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = DragonEngine(this)
    }
}
