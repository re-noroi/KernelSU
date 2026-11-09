package me.weishu.kernelsu.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material"),
    Minimal("minimal");

    companion object {
        fun fromValue(value: String): UiMode = when (value) {
            Miuix.value -> Miuix
            Minimal.value -> Minimal
            else -> Material
        }

        fun indexOfValue(value: String): Int = entries.indexOfFirst { it.value == value }.coerceAtLeast(0)

        val DEFAULT_VALUE = Material.value
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Material }
