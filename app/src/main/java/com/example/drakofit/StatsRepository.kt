package com.example.drakofit

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf

object StatsRepository {

    var distanceMeters = mutableStateOf(0f)

    val steps: State<Int> = derivedStateOf {
        (distanceMeters.value / 1000f * 1300f).toInt()
    }

    fun updateDistance(meters: Float) {
        distanceMeters.value = meters
    }
}