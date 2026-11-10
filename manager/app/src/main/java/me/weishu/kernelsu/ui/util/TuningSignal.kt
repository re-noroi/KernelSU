package me.weishu.kernelsu.ui.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A lightweight in-process signal that tells any open TuningViewModel
 * "the sysfs state changed — please refresh".
 *
 * This is event-driven, not polling: refresh() only runs when a toggle
 * actually happens (from the QS tile or from elsewhere in the app).
 */
object TuningSignal {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}