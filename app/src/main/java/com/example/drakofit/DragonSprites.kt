package com.example.drakofit

fun getDragonSprite(level: Int): Int {
    return when (level.coerceIn(1, 13)) {
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
}