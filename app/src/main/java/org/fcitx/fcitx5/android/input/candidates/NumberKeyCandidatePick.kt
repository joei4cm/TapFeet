/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.candidates

/**
 * Map physical number keys 1–5 (and numpad 1–5) onto the current candidate page.
 *
 * Returns a 0-based sequence index, or null when the key should still type: V-mode (`v` + digits),
 * Shift/Ctrl/Sym/Fn held, no candidates, or a number past the visible count. Alt is allowed so
 * classic Alt+number pick still lands here when the event is `KEYCODE_1`…`5`.
 *
 * Key codes / meta bits are [android.view.KeyEvent] constants, kept as ints so this stays a JVM
 * unit-testable function.
 */
object NumberKeyCandidatePick {

    /** [android.view.KeyEvent.KEYCODE_1] */
    const val KEYCODE_1 = 8
    const val KEYCODE_2 = 9
    const val KEYCODE_3 = 10
    const val KEYCODE_4 = 11
    const val KEYCODE_5 = 12
    const val KEYCODE_NUMPAD_1 = 145
    const val KEYCODE_NUMPAD_2 = 146
    const val KEYCODE_NUMPAD_3 = 147
    const val KEYCODE_NUMPAD_4 = 148
    const val KEYCODE_NUMPAD_5 = 149

    private const val META_SHIFT_MASK = 0x00000001 or 0x00000040 or 0x00000080
    private const val META_CTRL_MASK = 0x00001000 or 0x00002000 or 0x00004000
    private const val META_META_MASK = 0x00010000 or 0x00020000 or 0x00040000
    private const val META_SYM_ON = 0x00000004
    private const val META_FUNCTION_ON = 0x00000008
    private const val BLOCKING_META =
        META_SHIFT_MASK or META_CTRL_MASK or META_META_MASK or META_SYM_ON or META_FUNCTION_ON

    fun index(
        keyCode: Int,
        metaState: Int,
        candidateCount: Int,
        vModeActive: Boolean,
    ): Int? {
        if (vModeActive || candidateCount <= 0) return null
        if (metaState and BLOCKING_META != 0) return null
        val index = when (keyCode) {
            KEYCODE_1, KEYCODE_NUMPAD_1 -> 0
            KEYCODE_2, KEYCODE_NUMPAD_2 -> 1
            KEYCODE_3, KEYCODE_NUMPAD_3 -> 2
            KEYCODE_4, KEYCODE_NUMPAD_4 -> 3
            KEYCODE_5, KEYCODE_NUMPAD_5 -> 4
            else -> return null
        }
        return index.takeIf { it < candidateCount }
    }
}
