/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.core.ChinesePinyinEngine
import org.fcitx.fcitx5.android.input.keyboard.PinyinEngineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChinesePinyinEngineTest {

    @Test
    fun bundledKindsMatchFcitxNames() {
        assertEquals("pinyin", PinyinEngineKind.Fcitx.uniqueName)
        assertEquals("shuangpin", PinyinEngineKind.Shuangpin.uniqueName)
        assertEquals("rime", PinyinEngineKind.Rime.uniqueName)
        assertEquals("wbx", PinyinEngineKind.Wubi.uniqueName)
        assertTrue(ChinesePinyinEngine.bundledNames.containsAll(listOf("pinyin", "shuangpin", "rime", "wbx")))
    }

    @Test
    fun addsSelectedEngineWithoutRemovingOthers() {
        assertEquals(
            listOf("rime", "pinyin", "keyboard-us"),
            ChinesePinyinEngine.applyEnabled(listOf("rime", "keyboard-us"), "pinyin"),
        )
    }

    @Test
    fun keepsAlreadyEnabledEngine() {
        assertEquals(
            listOf("pinyin", "keyboard-us"),
            ChinesePinyinEngine.applyEnabled(listOf("pinyin", "keyboard-us"), "pinyin"),
        )
    }

    @Test
    fun insertsAfterExistingChineseEngine() {
        assertEquals(
            listOf("keyboard-us", "pinyin", "shuangpin"),
            ChinesePinyinEngine.applyEnabled(listOf("keyboard-us", "pinyin"), "shuangpin"),
        )
    }

    @Test
    fun insertsChineseWhenMissing() {
        assertEquals(
            listOf("wbx", "keyboard-us"),
            ChinesePinyinEngine.applyEnabled(listOf("keyboard-us"), "wbx"),
        )
    }

    @Test
    fun activateWhenOnAnotherBundledEngine() {
        assertEquals("pinyin", ChinesePinyinEngine.shouldActivate("rime", "pinyin"))
        assertEquals("shuangpin", ChinesePinyinEngine.shouldActivate("pinyin", "shuangpin"))
        assertNull(ChinesePinyinEngine.shouldActivate("keyboard-us", "rime"))
        assertNull(ChinesePinyinEngine.shouldActivate("rime", "rime"))
        assertEquals("wbx", ChinesePinyinEngine.shouldActivate("keyboard-us", "wbx", force = true))
    }
}
