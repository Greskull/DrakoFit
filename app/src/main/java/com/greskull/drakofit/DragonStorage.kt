package com.greskull.drakofit

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
            .putFloat("dragonOffsetY", state.dragonOffsetY)
            .putFloat("statsOffsetY", state.statsOffsetY)
            .putFloat("mapDragonScale", state.mapDragonScale)
            .apply()
    }

    // -------------------------
    // CARGAR ESTADO
    // -------------------------

    fun load(): DragonState {

        val totalSteps = prefs.getInt("totalSteps", 0)

        return DragonState(
            dragonOffsetY =
                prefs.getFloat("dragonOffsetY", 0f),

            statsOffsetY =
                prefs.getFloat("statsOffsetY", 0f),

            mapDragonScale =
                prefs.getFloat("mapDragonScale", 1f),
            level = prefs.getInt("level", 1),
            xp = prefs.getInt("xp", 0),
            streak = prefs.getInt("streak", 0),
            happiness = prefs.getInt("happiness", 3),

            totalSteps = totalSteps,

            // 🔥 DAILY STEPS ES DERIVADO, NO PERSISTIDO
            dailySteps = prefs.getInt("dailySteps", 0),
            dailyDistanceKm = prefs.getFloat("dailyDistanceKm", 0f),

            lastActivityDate =
                prefs.getString(
                    "lastActivityDate",
                    LocalDate.now().toString()
                ) ?: LocalDate.now().toString(),
            activityRegisteredToday = prefs.getBoolean("activityRegisteredToday", false)
        )
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

    // ---------------------------------------------------
    // DRAGON SELECTION
    // ---------------------------------------------------

    fun saveCurrentDragon(type: DragonType) {

        prefs.edit()
            .putString("currentDragon", type.name)
            .apply()
    }

    fun loadCurrentDragon(): DragonType {

        val saved = prefs.getString(
            "currentDragon",
            DragonType.FIRE.name
        ) ?: DragonType.FIRE.name

        return DragonType.valueOf(saved)
    }
    // ---------------------------------------------------
    // RECOMPENSAS
    // ---------------------------------------------------

    // ---------------------------------------------------
// PROGRESO DE CADA DRAGÓN
// ---------------------------------------------------

    fun saveDragonProgress(
        type: DragonType,
        level: Int,
        xp: Int
    ) {

        val prefix = when (type) {
            DragonType.FIRE -> "fire"
            DragonType.WATER -> "water"
            DragonType.PLANT -> "plant"
        }

        prefs.edit()
            .putInt("${prefix}_level", level)
            .putInt("${prefix}_xp", xp)
            .apply()
    }

    fun loadDragonProgress(
        type: DragonType
    ): Pair<Int, Int> {

        val prefix = when (type) {
            DragonType.FIRE -> "fire"
            DragonType.WATER -> "water"
            DragonType.PLANT -> "plant"
        }

        val defaultLevel =
            if (type == DragonType.FIRE) 1 else 1

        val level =
            prefs.getInt("${prefix}_level", defaultLevel)

        val xp =
            prefs.getInt("${prefix}_xp", 0)

        return Pair(level, xp)
    }

    fun saveDragonUnlocked(
        type: DragonType,
        unlocked: Boolean
    ) {

        prefs.edit()
            .putBoolean("dragon_${type.name}", unlocked)
            .apply()

    }

    fun isDragonUnlocked(type: DragonType): Boolean {

        return prefs.getBoolean(
            "dragon_${type.name}",
            type == DragonType.FIRE
        )

    }
    // -------------------------
    // SKINS
    // -------------------------

    fun saveActiveSkin(type: SkinType) {

        prefs.edit()
            .putString("activeSkin", type.name)
            .apply()
    }

    fun loadActiveSkin(): SkinType {

        val value = prefs.getString(
            "activeSkin",
            SkinType.NORMAL.name
        ) ?: SkinType.NORMAL.name

        return try {

            SkinType.valueOf(value)

        } catch (e: Exception) {

            // Compatibilidad con versiones antiguas
            SkinType.NORMAL

        }
    }

    fun saveAuraEnabled(enabled: Boolean) {

        prefs.edit()
            .putBoolean("auraEnabled", enabled)
            .apply()
    }

    fun loadAuraEnabled(): Boolean {

        return prefs.getBoolean(
            "auraEnabled",
            false
        )
    }
    fun saveAuraUnlocked(unlocked: Boolean) {

        prefs.edit()
            .putBoolean("auraUnlocked", unlocked)
            .apply()

    }

    fun isAuraUnlocked(): Boolean {

        return prefs.getBoolean(
            "auraUnlocked",
            false
        )

    }

    fun saveSkinUnlocked(
        type: SkinType,
        unlocked: Boolean
    ) {

        prefs.edit()
            .putBoolean("skin_${type.name}", unlocked)
            .apply()
    }

    fun isSkinUnlocked(type: SkinType): Boolean {

        return prefs.getBoolean(
            "skin_${type.name}",
            type == SkinType.NORMAL
        )
    }

    fun hasShownBackgroundWarning(): Boolean {
        return prefs.getBoolean("background_warning_shown", false)
    }

    fun setBackgroundWarningShown() {
        prefs.edit()
            .putBoolean("background_warning_shown", true)
            .apply()
    }
}