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
            .putFloat("dragonOffsetY", state.dragonOffsetY)
            .putFloat("statsOffsetY", state.statsOffsetY)
            .putFloat("mapDragonScale", state.mapDragonScale)
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

    fun saveStatsOffset(value: Float) {

        prefs.edit()
            .putFloat("statsOffsetY", value)
            .apply()
    }

    fun getLastCheckDate(): String {
        return prefs.getString("lastCheckDate", "") ?: ""
    }
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

    fun unlockAura() {
        prefs.edit().putBoolean("reward_aura", true).apply()
    }

    fun hasAura(): Boolean {
        return prefs.getBoolean("reward_aura", false)
    }

    fun unlockShiny() {
        prefs.edit().putBoolean("reward_shiny", true).apply()
    }

    fun hasShiny(): Boolean {
        return prefs.getBoolean("reward_shiny", false)
    }

    fun unlockGolden() {
        prefs.edit().putBoolean("reward_golden", true).apply()
    }

    fun hasGolden(): Boolean {
        return prefs.getBoolean("reward_golden", false)
    }

    fun unlockWaterDragon() {
        prefs.edit().putBoolean("reward_water", true).apply()
    }

    fun hasWaterDragon(): Boolean {
        return prefs.getBoolean("reward_water", false)
    }

    fun unlockPlantDragon() {
        prefs.edit().putBoolean("reward_plant", true).apply()
    }

    fun hasPlantDragon(): Boolean {
        return prefs.getBoolean("reward_plant", false)
    }
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
}