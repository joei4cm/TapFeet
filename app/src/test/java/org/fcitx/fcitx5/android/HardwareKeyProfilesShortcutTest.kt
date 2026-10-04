/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.data.prefs.HardwareChord
import org.fcitx.fcitx5.android.data.prefs.HardwareKeyProfiles
import org.fcitx.fcitx5.android.input.shortcut.ShortcutAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareKeyProfilesShortcutTest {

    @Test
    fun eliteShortcutLeadersAreUnique() {
        val values = HardwareKeyProfiles.shortcutValuesFor(HardwareKeyProfiles.TITAN2_ELITE).values
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun blackberryShortcutLeadersAreUnique() {
        val values = HardwareKeyProfiles.shortcutValuesFor(HardwareKeyProfiles.BLACKBERRY).values
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun toggleImeDefaultsToSymNOnElite() {
        val values = HardwareKeyProfiles.shortcutValuesFor(HardwareKeyProfiles.TITAN2_ELITE)
        assertEquals(
            HardwareChord.compose(HardwareChord.SYM, "n"),
            values[ShortcutAction.ToggleIme]
        )
    }

    @Test
    fun toggleImeDefaultsToRightShiftNOnBlackberry() {
        val values = HardwareKeyProfiles.shortcutValuesFor(HardwareKeyProfiles.BLACKBERRY)
        assertEquals(
            HardwareChord.compose(HardwareChord.SHIFT_R, "n"),
            values[ShortcutAction.ToggleIme]
        )
    }

    @Test
    fun everyActionHasAPresetBinding() {
        val values = HardwareKeyProfiles.shortcutValuesFor(HardwareKeyProfiles.TITAN2_ELITE)
        assertEquals(ShortcutAction.entries.toSet(), values.keys)
    }

    @Test
    fun commitLatinDefaultsToSymWOnElite() {
        val values = HardwareKeyProfiles.shortcutValuesFor(HardwareKeyProfiles.TITAN2_ELITE)
        assertEquals(
            HardwareChord.compose(HardwareChord.SYM, "w"),
            values[ShortcutAction.CommitLatin]
        )
    }

    @Test
    fun pasteLastClipboardDefaultsToFnIOnElite() {
        val values = HardwareKeyProfiles.shortcutValuesFor(HardwareKeyProfiles.TITAN2_ELITE)
        assertEquals(
            HardwareChord.compose(HardwareChord.FN, "i"),
            values[ShortcutAction.PasteLastClipboard]
        )
    }

    @Test
    fun eliteSetupHintOnlyOnEliteProfiles() {
        assertTrue(HardwareKeyProfiles.showsEliteCandidateHint(HardwareKeyProfiles.TITAN2_ELITE))
        assertTrue(HardwareKeyProfiles.showsEliteCandidateHint(HardwareKeyProfiles.TITAN2_ELITE_MOD))
        assertFalse(HardwareKeyProfiles.showsEliteCandidateHint(HardwareKeyProfiles.BLACKBERRY))
        assertFalse(HardwareKeyProfiles.showsEliteCandidateHint(HardwareKeyProfiles.TT2))
    }

    @Test
    fun eliteOpensSymbolPickerOnSymNotFn() {
        val v = HardwareKeyProfiles.factoryValues(HardwareKeyProfiles.TITAN2_ELITE)
        assertEquals("NavFn", v[2])
        assertEquals("", v[5])
        assertEquals("", v[6])
        assertEquals("Sym", v[7])
    }

    @Test
    fun eliteModOpensSymbolPickerOnSymNotFn() {
        val v = HardwareKeyProfiles.factoryValues(HardwareKeyProfiles.TITAN2_ELITE_MOD)
        assertEquals("NavFn", v[4])
        assertEquals("Shift_R", v[5])
        assertEquals("Shift_L", v[6])
        assertEquals("Sym", v[7])
    }
}
