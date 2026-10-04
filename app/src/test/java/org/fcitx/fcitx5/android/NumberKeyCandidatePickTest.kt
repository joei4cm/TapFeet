/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.candidates.NumberKeyCandidatePick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberKeyCandidatePickTest {

    @Test
    fun mapsOneThroughFive() {
        assertEquals(0, NumberKeyCandidatePick.index(NumberKeyCandidatePick.KEYCODE_1, 0, 5, false))
        assertEquals(4, NumberKeyCandidatePick.index(NumberKeyCandidatePick.KEYCODE_5, 0, 5, false))
        assertEquals(2, NumberKeyCandidatePick.index(NumberKeyCandidatePick.KEYCODE_NUMPAD_3, 0, 5, false))
    }

    @Test
    fun ignoresPastVisibleCount() {
        assertNull(NumberKeyCandidatePick.index(NumberKeyCandidatePick.KEYCODE_5, 0, 3, false))
        assertNull(NumberKeyCandidatePick.index(NumberKeyCandidatePick.KEYCODE_1, 0, 0, false))
    }

    @Test
    fun skipsVMode() {
        assertNull(NumberKeyCandidatePick.index(NumberKeyCandidatePick.KEYCODE_1, 0, 5, true))
    }

    @Test
    fun skipsShiftAndCtrl() {
        assertNull(NumberKeyCandidatePick.index(NumberKeyCandidatePick.KEYCODE_1, 0x00000001, 5, false))
        assertNull(NumberKeyCandidatePick.index(NumberKeyCandidatePick.KEYCODE_2, 0x00001000, 5, false))
    }

    @Test
    fun altDoesNotBlock() {
        assertEquals(0, NumberKeyCandidatePick.index(NumberKeyCandidatePick.KEYCODE_1, 0x00000002, 5, false))
    }
}
