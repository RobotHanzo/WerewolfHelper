package dev.robothanzo.werewolf.utils

fun parseLong(value: Any?): Long? = when (value) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull()
    else -> null
}