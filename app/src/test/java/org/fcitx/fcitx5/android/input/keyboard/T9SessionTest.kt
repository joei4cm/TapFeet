/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class T9SessionTest {

    @Before
    fun resetSession() {
        T9Session.setActive(false)
        T9Session.clear()
    }

    @Test
    fun inactiveIgnoresPanelLatinSoQwertyCannotFillDigits() {
        T9Session.setActive(false)
        T9Session.onPanelLatin("hao")
        assertFalse(T9Session.hasPendingDigits())
    }

    @Test
    fun activePanelLatinTracksNineKeyDigits() {
        T9Session.setActive(true)
        T9Session.onPanelLatin("hao")
        assertTrue(T9Session.hasPendingDigits())
    }

    @Test
    fun deactivatingClearsPendingDigits() {
        T9Session.setActive(true)
        T9Session.onPanelLatin("hao")
        assertTrue(T9Session.hasPendingDigits())
        T9Session.setActive(false)
        assertFalse(T9Session.hasPendingDigits())
    }
}
