/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.prefs

import org.fcitx.fcitx5.android.core.RawConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class PinyinSpellPrefsTest {

    @Test
    fun applyAlwaysWritesSpellEnabledFalse() {
        val cfg = RawConfig(
            arrayOf(RawConfig(PinyinSpellPrefs.SPELL_ENABLED, "True")),
        )
        PinyinSpellPrefs.apply(cfg)
        assertEquals("False", cfg[PinyinSpellPrefs.SPELL_ENABLED].value)
    }

    @Test
    fun buildDoesNotForceEnglishOntoValidPinyin() {
        val gradle = repoFile("lib/fcitx5-chinese-addons/build.gradle.kts").readText()
        val cpp = repoFile(
            "lib/fcitx5-chinese-addons/src/main/cpp/fcitx5-chinese-addons/im/pinyin/pinyin.cpp"
        ).readText()
        assertFalse(gradle.contains("pinyin-english-latin"))
        assertFalse(gradle.contains("Continuous latin buffer"))
        assertFalse(cpp.contains("Continuous latin buffer"))
        assertFalse(cpp.contains("rawLatin"))
        assertFalse(
            File(
                repoFile("lib/fcitx5-chinese-addons/build.gradle.kts").parentFile,
                "patches/pinyin-english-latin.patch",
            ).exists()
        )
    }

    private fun repoFile(relative: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        throw AssertionError("missing $relative from ${System.getProperty("user.dir")}")
    }
}
