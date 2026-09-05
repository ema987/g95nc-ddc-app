package com.g95nc.controller

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM, DARK, LIGHT;

    val label: String
        get() = when (this) {
            SYSTEM -> "Follow System"
            DARK -> "Dark"
            LIGHT -> "Light"
        }
}
