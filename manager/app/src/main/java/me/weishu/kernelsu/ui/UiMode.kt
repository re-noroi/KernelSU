package me.weishu.kernelsu.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material"),
    Minimal("minimal");

    companion object {
        /**
         * The skin used when nothing is stored, and the fallback for unknown
         * values. Minimal is the default look now.
         */
        val DEFAULT_VALUE = Minimal.value

        /**
         * Modes offered in Settings. [Material] is no longer selectable; it stays
         * in the enum only so the shared Material composables keep a name and so
         * legacy stored values can be migrated.
         */
        val selectable: List<UiMode> = listOf(Miuix, Minimal)

        fun fromValue(value: String): UiMode = when (value) {
            Miuix.value -> Miuix
            // "material" used to be its own selectable skin. Anyone still on it
            // is moved to Minimal instead of getting a broken theme.
            else -> Minimal
        }

        fun indexOfSelectable(value: String): Int =
            selectable.indexOfFirst { it.value == value }.coerceAtLeast(0)
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Minimal }
