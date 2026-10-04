/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.core.RawConfig
import org.fcitx.fcitx5.android.data.prefs.AndroidKeyboardPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidKeyboardPrefsTest {

    @Test
    fun writesTrueFalseOnCfg() {
        val cfg = RawConfig()
        AndroidKeyboardPrefs.apply(cfg, wordHint = true, insertSpace = false)
        assertEquals("True", cfg[AndroidKeyboardPrefs.ENABLE_WORD_HINT].value)
        assertEquals("False", cfg[AndroidKeyboardPrefs.INSERT_SPACE].value)
    }

    @Test
    fun overwritesExistingWithoutDroppingOthers() {
        val cfg = RawConfig(
            arrayOf(
                RawConfig(AndroidKeyboardPrefs.ENABLE_WORD_HINT, false),
                RawConfig(AndroidKeyboardPrefs.INSERT_SPACE, false),
                RawConfig("PageSize", "5"),
            )
        )
        AndroidKeyboardPrefs.apply(cfg, wordHint = true, insertSpace = true)
        assertEquals("True", cfg[AndroidKeyboardPrefs.ENABLE_WORD_HINT].value)
        assertEquals("True", cfg[AndroidKeyboardPrefs.INSERT_SPACE].value)
        assertEquals("5", cfg["PageSize"].value)
    }

    @Test
    fun nestedCfgFromGetAddonConfig() {
        val raw = RawConfig(
            arrayOf(
                RawConfig("cfg", arrayOf(RawConfig(AndroidKeyboardPrefs.ENABLE_WORD_HINT, "False"))),
                RawConfig("desc", arrayOf()),
            )
        )
        AndroidKeyboardPrefs.apply(raw["cfg"], wordHint = true, insertSpace = true)
        assertEquals("True", raw["cfg"][AndroidKeyboardPrefs.ENABLE_WORD_HINT].value)
        assertEquals("True", raw["cfg"][AndroidKeyboardPrefs.INSERT_SPACE].value)
    }
}
