package com.example.drakofit
data class RouteHistoryEntry(

    val id: Long = System.currentTimeMillis(),

    val timestamp: Long = System.currentTimeMillis(),

    val durationSeconds: Long,

    val distanceKm: Float,

    val steps: Int,

    val calories: Int,

    val pace: String,

    val xpEarned: Int
)