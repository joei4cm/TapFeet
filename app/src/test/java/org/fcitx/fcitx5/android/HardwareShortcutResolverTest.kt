/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.candidates.HardwareShortcutLogic
import org.fcitx.fcitx5.android.input.candidates.horizontal.CandidateArrangementMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareShortcutResolverTest {

    @Test
    fun pagingIgnoresUnmatchedKeys() {
        assertNull(HardwareShortcutLogic.pagingDirection(false, false, false, false))
    }

    @Test
    fun pagingPlainNextAndPrev() {
        assertEquals(1, HardwareShortcutLogic.pagingDirection(true, false, false, false))
        assertEquals(-1, HardwareShortcutLogic.pagingDirection(false, true, false, false))
    }

    @Test
    fun pagingComboBeatsPlainOnSameKey() {
        assertEquals(
            -1,
            HardwareShortcutLogic.pagingDirection(
                nextMatches = true,
                prevMatches = true,
                nextHasModifier = false,
                prevHasModifier = true,
            ),
        )
        assertEquals(
            1,
            HardwareShortcutLogic.pagingDirection(
                nextMatches = true,
                prevMatches = true,
                nextHasModifier = true,
                prevHasModifier = false,
            ),
        )
    }

    @Test
    fun firstPickIsCenterInMacrohard() {
        assertEquals(0, HardwareShortcutLogic.firstPickPosition(1, CandidateArrangementMode.Macrohard))
        assertEquals(1, HardwareShortcutLogic.firstPickPosition(3, CandidateArrangementMode.Macrohard))
        assertEquals(2, HardwareShortcutLogic.firstPickPosition(5, CandidateArrangementMode.Macrohard))
    }

    @Test
    fun firstPickIsLeftInLinear() {
        assertEquals(0, HardwareShortcutLogic.firstPickPosition(1, CandidateArrangementMode.Linear))
        assertEquals(0, HardwareShortcutLogic.firstPickPosition(5, CandidateArrangementMode.Linear))
    }
}
