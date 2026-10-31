/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

import org.fcitx.fcitx5.android.core.RawConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishInputModeTest {

    @Test
    fun wordHintEnablesSpellAndOptionalSpace() {
        val cfg = RawConfig("cfg", arrayOf())
        AndroidKeyboardPrefs.apply(cfg, wordHint = true, insertSpace = true)
        assertEquals("True", cfg[AndroidKeyboardPrefs.ENABLE_WORD_HINT].value)
        assertEquals("True", cfg[AndroidKeyboardPrefs.INSERT_SPACE].value)
    }

    @Test
    fun literalDisablesSpell() {
        val cfg = RawConfig("cfg", arrayOf())
        AndroidKeyboardPrefs.apply(cfg, wordHint = false, insertSpace = true)
        assertEquals("False", cfg[AndroidKeyboardPrefs.ENABLE_WORD_HINT].value)
        // insertSpace is still written; sync path ANDs with wordHint before apply.
        assertEquals("True", cfg[AndroidKeyboardPrefs.INSERT_SPACE].value)
    }

    @Test
    fun modesAreNamedForSettings() {
        assertEquals(2, EnglishInputMode.entries.size)
        assertTrue(EnglishInputMode.WordHint.stringRes != 0)
        assertTrue(EnglishInputMode.Literal.stringRes != 0)
        assertFalse(EnglishInputMode.WordHint.name == EnglishInputMode.Literal.name)
    }
}
