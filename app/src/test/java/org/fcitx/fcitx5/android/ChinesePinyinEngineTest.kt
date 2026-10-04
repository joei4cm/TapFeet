/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.core.ChinesePinyinEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChinesePinyinEngineTest {

    @Test
    fun replacesPinyinWithRimeInPlace() {
        assertEquals(
            listOf("rime", "keyboard-us"),
            ChinesePinyinEngine.applyEnabled(listOf("pinyin", "keyboard-us"), useRime = true),
        )
    }

    @Test
    fun replacesRimeWithPinyinInPlace() {
        assertEquals(
            listOf("pinyin", "keyboard-us"),
            ChinesePinyinEngine.applyEnabled(listOf("rime", "keyboard-us"), useRime = false),
        )
    }

    @Test
    fun keepsEnglishFirst() {
        assertEquals(
            listOf("keyboard-us", "rime"),
            ChinesePinyinEngine.applyEnabled(listOf("keyboard-us", "pinyin"), useRime = true),
        )
    }

    @Test
    fun insertsChineseWhenMissing() {
        assertEquals(
            listOf("rime", "keyboard-us"),
            ChinesePinyinEngine.applyEnabled(listOf("keyboard-us"), useRime = true),
        )
    }

    @Test
    fun keepsOnlyOneChineseEngine() {
        assertEquals(
            listOf("rime", "keyboard-us"),
            ChinesePinyinEngine.applyEnabled(
                listOf("pinyin", "rime", "keyboard-us"),
                useRime = true,
            ),
        )
    }

    @Test
    fun activateOnlyWhenOnTheOtherEngine() {
        assertEquals("rime", ChinesePinyinEngine.shouldActivate("pinyin", useRime = true))
        assertEquals("pinyin", ChinesePinyinEngine.shouldActivate("rime", useRime = false))
        assertNull(ChinesePinyinEngine.shouldActivate("keyboard-us", useRime = true))
        assertNull(ChinesePinyinEngine.shouldActivate("rime", useRime = true))
    }
}
