/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.prefs

import org.fcitx.fcitx5.android.core.RawConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class PinyinSpellPrefsTest {

    @Test
    fun applyWritesSpellEnabledTrue() {
        val cfg = RawConfig()
        PinyinSpellPrefs.apply(cfg, enabled = true)
        assertEquals("True", cfg[PinyinSpellPrefs.SPELL_ENABLED].value)
    }

    @Test
    fun applyWritesSpellEnabledFalse() {
        val cfg = RawConfig(
            arrayOf(RawConfig(PinyinSpellPrefs.SPELL_ENABLED, "True")),
        )
        PinyinSpellPrefs.apply(cfg, enabled = false)
        assertEquals("False", cfg[PinyinSpellPrefs.SPELL_ENABLED].value)
    }
}
