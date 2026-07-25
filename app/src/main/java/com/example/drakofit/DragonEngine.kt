package com.example.drakofit

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate
import kotlin.math.pow

class DragonEngine(context: Context) {

    private val storage = DragonStorage(context)

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

        updateState(
            _state.value.copy(
                xp = newXp,
                level = newLevel
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
        val xpGain = steps / 100
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

        val lastActivity = try {
            LocalDate.parse(old.lastActivityDate)
        } catch (e: Exception) {
            todayDate
        }

        val isNewDay = old.lastDailyCheckDate != today

        val missedDay = lastActivity.isBefore(todayDate.minusDays(1))

        val resetDaily = if (isNewDay) {
            old.copy(
                dailySteps = 0,
                dailyDistanceKm = 0f,
                activityRegisteredToday = false,
                lastDailyCheckDate = today
            )
        } else {
            old
        }

        return if (missedDay) {
            resetDaily.copy(
                streak = 0,
                happiness = (old.happiness - 1).coerceAtLeast(0)
            )
        } else {
            resetDaily
        }
    }
}