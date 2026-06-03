package com.example.drakofit

import android.content.Context
import java.time.LocalDate

class DragonStorage(context: Context) {

    private val prefs = context.getSharedPreferences(
        "drakofit_prefs",
        Context.MODE_PRIVATE
    )

    // -------------------------
    // GUARDAR ESTADO
    // -------------------------

    fun save(state: DragonState) {

        android.util.Log.d(
            "DRAGON_SAVE",
            "Saving → totalSteps=${state.totalSteps} dailySteps=${state.dailySteps}"
        )

        prefs.edit()
            .putInt("level", state.level)
            .putInt("xp", state.xp)
            .putInt("streak", state.streak)
            .putInt("happiness", state.happiness)
            .putInt("totalSteps", state.totalSteps)
            .putString("lastActivityDate", state.lastActivityDate)
            .putBoolean("activityRegisteredToday", state.activityRegisteredToday)
            .putString("lastCheckDate", getToday())
            .putInt("dailySteps", state.dailySteps)
            .putFloat("dailyDistanceKm", state.dailyDistanceKm)
            .apply()
    }

    // -------------------------
    // CARGAR ESTADO
    // -------------------------

    fun load(): DragonState {

        val today = getToday()
        val lastCheckDate = prefs.getString("lastCheckDate", "") ?: ""

        val isNewDay = lastCheckDate != today

        val totalSteps = prefs.getInt("totalSteps", 0)

        return DragonState(
            level = prefs.getInt("level", 1),
            xp = prefs.getInt("xp", 0),
            streak = prefs.getInt("streak", 0),
            happiness = prefs.getInt("happiness", 5),

            totalSteps = totalSteps,

            // 🔥 DAILY STEPS ES DERIVADO, NO PERSISTIDO
            dailySteps = prefs.getInt("dailySteps", 0),
            dailyDistanceKm = prefs.getFloat("dailyDistanceKm", 0f),

            lastActivityDate = prefs.getString("lastActivityDate", "") ?: "",
            activityRegisteredToday = prefs.getBoolean("activityRegisteredToday", false)
        ).also {

            // 🔥 RESET REAL DE DÍA NUEVO (UNA SOLA VEZ)
            if (isNewDay) {
                prefs.edit()
                    .putString("lastCheckDate", today)
                    .putInt("dailySteps", 0)
                    .putFloat("dailyDistanceKm", 0f)
                    .apply()
            }
        }
    }

    // -------------------------
    // UTILIDAD INTERNA
    // -------------------------

    private fun getToday(): String {
        return LocalDate.now().toString()
    }

    // -------------------------
    // COMPATIBILIDAD (opcional)
    // -------------------------

    fun getLastCheckDate(): String {
        return prefs.getString("lastCheckDate", "") ?: ""
    }
}