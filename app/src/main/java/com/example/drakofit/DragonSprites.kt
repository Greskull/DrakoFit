package com.example.drakofit

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
fun getDragonSprite(
    dragon: DragonType,
    level: Int
): Int {

    val stage = level.coerceIn(1, 13)

    return when (dragon) {

        DragonType.FIRE -> when (stage) {
            1 -> R.drawable.dragon_sprite_1
            2 -> R.drawable.dragon_sprite_2
            3 -> R.drawable.dragon_sprite_3
            4 -> R.drawable.dragon_sprite_4
            5 -> R.drawable.dragon_sprite_5
            6 -> R.drawable.dragon_sprite_6
            7 -> R.drawable.dragon_sprite_7
            8 -> R.drawable.dragon_sprite_8
            9 -> R.drawable.dragon_sprite_9
            10 -> R.drawable.dragon_sprite_10
            11 -> R.drawable.dragon_sprite_11
            12 -> R.drawable.dragon_sprite_12
            else -> R.drawable.dragon_sprite_13
        }

        DragonType.WATER -> when (stage) {
            1 -> R.drawable.dragon_sprite_agua_1
            2 -> R.drawable.dragon_sprite_agua_2
            3 -> R.drawable.dragon_sprite_agua_3
            4 -> R.drawable.dragon_sprite_agua_4
            5 -> R.drawable.dragon_sprite_agua_5
            6 -> R.drawable.dragon_sprite_agua_6
            7 -> R.drawable.dragon_sprite_agua_7
            8 -> R.drawable.dragon_sprite_agua_8
            9 -> R.drawable.dragon_sprite_agua_9
            10 -> R.drawable.dragon_sprite_agua_10
            11 -> R.drawable.dragon_sprite_agua_11
            12 -> R.drawable.dragon_sprite_agua_12
            else -> R.drawable.dragon_sprite_agua_13
        }

        DragonType.PLANT -> when (stage) {
            1 -> R.drawable.dragon_sprite_planta_1
            2 -> R.drawable.dragon_sprite_planta_2
            3 -> R.drawable.dragon_sprite_planta_3
            4 -> R.drawable.dragon_sprite_planta_4
            5 -> R.drawable.dragon_sprite_planta_5
            6 -> R.drawable.dragon_sprite_planta_6
            7 -> R.drawable.dragon_sprite_planta_7
            8 -> R.drawable.dragon_sprite_planta_8
            9 -> R.drawable.dragon_sprite_planta_9
            10 -> R.drawable.dragon_sprite_planta_10
            11 -> R.drawable.dragon_sprite_planta_11
            12 -> R.drawable.dragon_sprite_planta_12
            else -> R.drawable.dragon_sprite_planta_13
        }
    }
}

fun getDragonColorFilter(
    skin: SkinType,
    dragon: DragonType
): ColorFilter? {

    if (skin == SkinType.NORMAL)
        return null

    val matrix = when (skin) {

        SkinType.GOLD -> {

            when (dragon) {

                DragonType.FIRE -> ColorMatrix(

                    floatArrayOf(

                        // R
                        1.10f, 0.15f, 0.00f, 0f, 8f,

                        // G
                        0.18f, 1.00f, 0.00f, 0f, 6f,

                        // B
                        0.00f, 0.00f, 0.60f, 0f, -4f,

                        // A
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                DragonType.WATER -> ColorMatrix(

                    floatArrayOf(

                        // R
                        1.00f, 0.15f, 0.25f, 0f, 10f,

                        // G
                        0.05f, 1.00f, 0.15f, 0f, 8f,

                        // B
                        0.00f, 0.00f, 0.35f, 0f, -8f,

                        // A
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                DragonType.PLANT -> ColorMatrix(

                    floatArrayOf(

                        // R
                        1.10f, 0.35f, 0.00f, 0f, 10f,

                        // G
                        0.10f, 1.00f, 0.00f, 0f, 8f,

                        // B
                        0.00f, 0.00f, 0.40f, 0f, -8f,

                        // A
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
        }

        SkinType.SHINY -> {

            when (dragon) {

                DragonType.FIRE -> ColorMatrix(

                    floatArrayOf(

                        // R
                        1.10f, 0.12f, 0.12f, 0f, 10f,

                        // G
                        0.05f, 0.70f, 0.20f, 0f, -8f,

                        // B
                        0.35f, 0.05f, 1.10f, 0f, 18f,

                        // A
                        0f, 0f, 0f, 1f, 0f

                    )

                )
                DragonType.WATER -> ColorMatrix(
                    floatArrayOf(

                        // R
                        0.85f, 0.10f, 0.15f, 0f, 0f,

                        // G
                        0.10f, 1.15f, 0.20f, 0f, 10f,

                        // B
                        0.00f, 0.10f, 1.05f, 0f, 5f,

                        // A
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                DragonType.PLANT -> ColorMatrix(
                    floatArrayOf(

                        // R
                        0.70f, 0.18f, 0.00f, 0f, -8f,

                        // G
                        0.15f, 0.95f, 0.00f, 0f, -4f,

                        // B
                        0.00f, 0.00f, 0.22f, 0f, -18f,

                        // A
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
        }
        else -> return null
    }
    return ColorFilter.colorMatrix(matrix)
}
