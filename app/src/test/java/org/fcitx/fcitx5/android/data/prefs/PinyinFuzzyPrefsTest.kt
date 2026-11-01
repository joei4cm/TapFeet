/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.prefs

import org.fcitx.fcitx5.android.core.RawConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class PinyinFuzzyPrefsTest {

    @Test
    fun keepsNasalFuzzyButClearsInitialFuzzy() {
        val cfg = RawConfig(
            arrayOf(
                RawConfig(
                    "Fuzzy",
                    arrayOf(
                        RawConfig("Z_ZH", "True"),
                        RawConfig("C_CH", "True"),
                        RawConfig("S_SH", "True"),
                        RawConfig("AN_ANG", "False"),
                    ),
                ),
            ),
        )
        PinyinFuzzyPrefs.apply(cfg)
        val fuzzy = cfg["Fuzzy"]
        assertEquals("False", fuzzy["Z_ZH"].value)
        assertEquals("False", fuzzy["C_CH"].value)
        assertEquals("False", fuzzy["S_SH"].value)
        assertEquals("True", fuzzy["AN_ANG"].value)
        assertEquals("True", fuzzy["EN_ENG"].value)
        assertEquals("Qwerty", fuzzy["Correction"].value)
    }
}
