package com.example.drakofit

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