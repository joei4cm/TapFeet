/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.candidates.HardwareShortcutResolver
import org.fcitx.fcitx5.android.input.candidates.horizontal.CandidateArrangementMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareShortcutResolverTest {

    @Test
    fun pagingIgnoresUnmatchedKeys() {
        assertNull(HardwareShortcutResolver.pagingDirection(false, false, false, false))
    }

    @Test
    fun pagingPlainNextAndPrev() {
        assertEquals(1, HardwareShortcutResolver.pagingDirection(true, false, false, false))
        assertEquals(-1, HardwareShortcutResolver.pagingDirection(false, true, false, false))
    }

    @Test
    fun pagingComboBeatsPlainOnSameKey() {
        assertEquals(
            -1,
            HardwareShortcutResolver.pagingDirection(
                nextMatches = true,
                prevMatches = true,
                nextHasModifier = false,
                prevHasModifier = true,
            ),
        )
        assertEquals(
            1,
            HardwareShortcutResolver.pagingDirection(
                nextMatches = true,
                prevMatches = true,
                nextHasModifier = true,
                prevHasModifier = false,
            ),
        )
    }

    @Test
    fun firstPickIsCenterInMacrohard() {
        assertEquals(0, HardwareShortcutResolver.firstPickPosition(1, CandidateArrangementMode.Macrohard))
        assertEquals(1, HardwareShortcutResolver.firstPickPosition(3, CandidateArrangementMode.Macrohard))
        assertEquals(2, HardwareShortcutResolver.firstPickPosition(5, CandidateArrangementMode.Macrohard))
    }

    @Test
    fun firstPickIsLeftInLinear() {
        assertEquals(0, HardwareShortcutResolver.firstPickPosition(1, CandidateArrangementMode.Linear))
        assertEquals(0, HardwareShortcutResolver.firstPickPosition(5, CandidateArrangementMode.Linear))
    }
}
