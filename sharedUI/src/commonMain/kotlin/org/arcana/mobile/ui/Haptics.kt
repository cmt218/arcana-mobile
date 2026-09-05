package org.arcana.mobile.ui

import androidx.compose.runtime.Composable

/** The phone's vocabulary, matched to the screen's. Eight verbs, used consistently. */
interface Haptics {
    /** Tab change, day chip tap, spot pick. */
    fun selection()
    /** Each page boundary while scrubbing days. Very light, repeatable. */
    fun tick()
    /** Filter chip and favourite on / off. Two distinct weights. */
    fun toggle(on: Boolean)
    /** Slide-to-book arms, pull-to-refresh triggers, a sheet passes its dismiss point. */
    fun threshold()
    /** Booking succeeds, profile saved. */
    fun confirm()
    /** Booking fails, hold-to-cancel completes. */
    fun reject()
    /** Overscroll at the end of a list. Soft. */
    fun boundary()
    /** Hold-to-cancel while holding; the caller repeats it on a timer. */
    fun ramp()
}

object NoHaptics : Haptics {
    override fun selection() = Unit
    override fun tick() = Unit
    override fun toggle(on: Boolean) = Unit
    override fun threshold() = Unit
    override fun confirm() = Unit
    override fun reject() = Unit
    override fun boundary() = Unit
    override fun ramp() = Unit
}

@Composable
expect fun rememberHaptics(): Haptics
