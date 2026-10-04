/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.swipe

/**
 * Keyboard-surface hold-to-talk: a still finger for [HOLD_MS] starts recording; travel past the
 * swipe slop (or a two-finger/swipe classification) cancels the pending hold; lift stops if it
 * had started. Once started, wobble is ignored so PTT is not turned into a swipe.
 */
class HoldVoiceTracker {

    enum class Command { Start, Stop, None }

    var started: Boolean = false
        private set

    private var down = false
    private var cancelled = false

    fun down() {
        down = true
        started = false
        cancelled = false
    }

    /** Call when the delayed hold elapses. */
    fun elapsed(): Command {
        if (!down || cancelled || started) return Command.None
        started = true
        return Command.Start
    }

    /** Call when the contact becomes a swipe / two-finger / typing graze. */
    fun cancel(): Command {
        if (!down) return Command.None
        cancelled = true
        if (started) {
            started = false
            down = false
            return Command.Stop
        }
        return Command.None
    }

    fun up(): Command {
        val stop = started
        reset()
        return if (stop) Command.Stop else Command.None
    }

    fun reset() {
        down = false
        started = false
        cancelled = false
    }

    companion object {
        const val HOLD_MS = 350L
    }
}
