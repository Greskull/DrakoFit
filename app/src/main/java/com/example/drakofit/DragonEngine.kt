package com.example.drakofit

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate
import kotlin.math.pow

class DragonEngine(context: Context) {

    private val storage = DragonStorage(context)

    // 🔥 ESTADO ÚNICO
    private val _state = mutableStateOf<DragonState>(
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

        // 🔥 FORZAR DISCO INMEDIATO
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
                lastActivityDate = today
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
                lastActivityDate = today
            )
        )
    }

    fun addWalkingXP(steps: Int) {
        val xpGain = steps / 100
        addXp(xpGain)
    }

    fun addRunningXP(km: Float) {
        val xpGain = (km * 50).toInt()
        addXp(xpGain)
    }

    // ---------------------------------------------------
    // DAILY
    // ---------------------------------------------------

    fun registerActivity() {

        val today = LocalDate.now().toString()

        if (!_state.value.activityRegisteredToday) {

            updateState(
                _state.value.copy(
                    streak = _state.value.streak + 1,
                    happiness = (_state.value.happiness + 1).coerceAtMost(7),
                    lastActivityDate = today,
                    activityRegisteredToday = true
                )
            )
        }
    }

    fun missDay() {
        updateState(
            _state.value.copy(
                streak = 0,
                happiness = (_state.value.happiness - 1).coerceAtLeast(0)
            )
        )
    }

    fun checkDailyStatus() {

        val today = LocalDate.now().toString()

        val last = _state.value.lastActivityDate

        if (last == today) return

        updateState(
            _state.value.copy(
                dailySteps = 0,
                activityRegisteredToday = false,
                lastActivityDate = today
            )
        )
    }

    // ---------------------------------------------------
    // NORMALIZACIÓN (ÚNICA FUENTE)
    // ---------------------------------------------------

    private fun normalizeDailyState(old: DragonState): DragonState {

        val today = LocalDate.now().toString()

        return if (old.lastActivityDate != today) {
            old.copy(
                dailySteps = 0,
                dailyDistanceKm = 0f,
                activityRegisteredToday = false,
                lastActivityDate = today
            )
        } else {
            old
        }
    }
}