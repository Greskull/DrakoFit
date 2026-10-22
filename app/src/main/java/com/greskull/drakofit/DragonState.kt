package com.greskull.drakofit

import java.time.LocalDate

data class DragonState(
    val lastDailyCheckDate: String = LocalDate.now().toString(),
    val level: Int = 1,
    val xp: Int = 0,
    val streak: Int = 0,

    // Felicidad del dragón (máximo 7)
    val happiness: Int = 3,

    // Pasos acumulados históricos
    val totalSteps: Int = 0,

    // Pasos del día (se resetean cada medianoche)
    val dailySteps: Int = 0,
    // Distancia diaria (también se resetea)
    val dailyDistanceKm: Float = 0f,

    // Fecha del último día en que se hizo actividad
    // Formato esperado: "2026-05-14"
    val lastActivityDate: String = "",

    // Evita sumar felicidad varias veces en el mismo día
    val activityRegisteredToday: Boolean = false,

    // Ajustes de interfaz
    val dragonOffsetY: Float = 0f,
    val statsOffsetY: Float = 0f,
    val mapDragonScale: Float = 1f,
    //Darkmode
    val isMapDarkMode: Boolean = false

)