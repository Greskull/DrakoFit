package com.example.drakofit

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToInt

class DragonEngine(context: Context) {

    private val storage = DragonStorage(context)
    //Función distintos dragones
    private var currentDragon =
        storage.loadCurrentDragon()
    private var activeSkin = storage.loadActiveSkin()
    private var auraEnabled = storage.loadAuraEnabled()

    // 🔥 ESTADO ÚNICO
    private val _state = mutableStateOf(
        normalizeDailyState(storage.load())
    )

    val state: State<DragonState> = _state

    fun getState(): DragonState = _state.value

    // ---------------------------------------------------
    // XP
    // ---------------------------------------------------

    fun getRequiredXpForNextLevel(): Int {
        return (100 * 1.5.pow(_state.value.level - 1)).toInt()
    }

    // ---------------------------------------------------
    // UPDATE CENTRAL
    // ---------------------------------------------------

    private fun updateState(newState: DragonState) {
        _state.value = newState
        storage.save(newState)

        android.os.StrictMode.noteSlowCall("Saving DragonState")
    }

    // ---------------------------------------------------
    // XP
    // ---------------------------------------------------

    private fun addXp(xpGain: Int) {

        var newXp = _state.value.xp + xpGain
        var newLevel = _state.value.level

        var requiredXp = (100 * 1.5.pow(newLevel - 1)).toInt()

        while (newXp >= requiredXp) {
            newXp -= requiredXp
            newLevel += 1
            requiredXp = (100 * 1.5.pow(newLevel - 1)).toInt()
        }

        val newState = _state.value.copy(
            xp = newXp,
            level = newLevel
        )

        updateState(newState)

        // Guardar el progreso del dragón activo
        storage.saveDragonProgress(
            currentDragon,
            newState.level,
            newState.xp
        )
    }

    fun resetDragon() {
        val safeState = DragonState() // estado base limpio

        updateState(
            safeState.copy(
                lastDailyCheckDate = LocalDate.now().toString()
            )
        )
    }

    // ---------------------------------------------------
    // PASOS
    // ---------------------------------------------------

    fun addSteps(steps: Int) {

        val today = LocalDate.now().toString()

        updateState(
            _state.value.copy(
                totalSteps = _state.value.totalSteps + steps,
                dailySteps = _state.value.dailySteps + steps,
            )
        )

        addWalkingXP(steps)
    }

    // ---------------------------------------------------
    // DISTANCIA
    // ---------------------------------------------------

    fun addDistance(km: Float) {

        val today = LocalDate.now().toString()

        updateState(
            _state.value.copy(
                dailyDistanceKm = _state.value.dailyDistanceKm + km,
            )
        )
    }

    fun addWalkingXP(steps: Int) {
        val baseXp = steps / 100
        val xpGain = (baseXp * (1f + _state.value.streak * 0.01f)).roundToInt()
        addXp(xpGain)
    }

    // ---------------------------------------------------
    // DAILY
    // ---------------------------------------------------

    fun registerActivity() {

        val today = LocalDate.now().toString()

        // 🔥 ya registró actividad hoy → no hacer nada
        if (_state.value.lastActivityDate == today &&
            _state.value.activityRegisteredToday
        ) return

        updateState(
            _state.value.copy(
                streak = _state.value.streak + 1,
                happiness = (_state.value.happiness + 1).coerceAtMost(7),
                lastActivityDate = today,
                activityRegisteredToday = true
            )
        )
    }

    fun missDay() {
        updateState(
            _state.value.copy(
                streak = 0,
                happiness = (_state.value.happiness - 1).coerceAtLeast(0)
            )
        )
    }

    fun setDragonOffset(offset: Float) {

        updateState(
            _state.value.copy(
                dragonOffsetY = offset
            )
        )

    }

    fun setStatsOffset(offset: Float) {

        updateState(
            _state.value.copy(
                statsOffsetY = offset
            )
        )

    }

    fun setMapDragonScale(scale: Float) {

        updateState(
            _state.value.copy(
                mapDragonScale = scale
            )
        )

    }

    //Darkmode
    fun setMapDarkMode(enabled: Boolean) {
        updateState(
            _state.value.copy(
                isMapDarkMode = enabled
            )
        )
    }

    // ---------------------------------------------------
    // DAILY CHECK (FIX PRINCIPAL)
    // ---------------------------------------------------

    fun checkDailyStatus() {
        // ya no necesario (evita doble lógica)
    }

    // ---------------------------------------------------
    // NORMALIZACIÓN (CARGA INICIAL)
    // ---------------------------------------------------

    private fun normalizeDailyState(old: DragonState): DragonState {

        val todayDate = LocalDate.now()
        val today = todayDate.toString()

        val lastActivityDate = old.lastActivityDate

        val safeLastActivity = try {
            if (lastActivityDate.isBlank()) {
                todayDate
            } else {
                LocalDate.parse(lastActivityDate)
            }
        } catch (e: Exception) {
            todayDate
        }

        val isNewDay = safeLastActivity.toString() != today

        val missedDay = safeLastActivity.isBefore(todayDate.minusDays(1))

        val resetDaily = if (isNewDay) {
            old.copy(
                dailySteps = 0,
                dailyDistanceKm = 0f,
                activityRegisteredToday = false,
                lastDailyCheckDate = today
            )
        } else old

        return if (missedDay) {
            resetDaily.copy(
                streak = 0,
                happiness = (old.happiness - 1).coerceAtLeast(0)
            )
        } else {
            resetDaily
        }
    }
    //Funciones para distintos dragones

    fun getCurrentDragon(): DragonType {

        return currentDragon
    }

    fun setCurrentDragon(type: DragonType) {

        // Si ya está seleccionado, no hacemos nada
        if (type == currentDragon) return

        // Guardamos el progreso del dragón actual
        storage.saveDragonProgress(
            currentDragon,
            _state.value.level,
            _state.value.xp
        )

        // Cambiamos el dragón activo
        currentDragon = type
        storage.saveCurrentDragon(type)

        // Cargamos el progreso del nuevo dragón
        val (level, xp) = storage.loadDragonProgress(type)

        // Actualizamos únicamente nivel y experiencia
        updateState(
            _state.value.copy(
                level = level,
                xp = xp
            )
        )
    }
    fun getActiveSkin(): SkinType {

        return activeSkin
    }

    fun setActiveSkin(type: SkinType) {

        if (!storage.isSkinUnlocked(type)) return

        activeSkin = type

        storage.saveActiveSkin(type)
    }

    fun unlockSkin(type: SkinType) {

        storage.saveSkinUnlocked(type, true)
    }

    fun isSkinUnlocked(type: SkinType): Boolean {

        return storage.isSkinUnlocked(type)
    }

    fun isAuraEnabled(): Boolean {

        return auraEnabled
    }

    fun setAuraEnabled(enabled: Boolean) {

        auraEnabled = enabled

        storage.saveAuraEnabled(enabled)
    }
}
