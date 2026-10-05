/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundledCjkFontTest {

    @Test
    fun bundledNotoSansScIsOpenTypeWithOfl() {
        val font = listOf(
            File("src/main/assets/fonts/NotoSansSC-Regular.otf"),
            File("app/src/main/assets/fonts/NotoSansSC-Regular.otf"),
        ).firstOrNull { it.isFile } ?: error("bundled Noto Sans SC is missing")
        assertTrue(font.length() > 4_000_000L)
        val tag = ByteArray(4)
        font.inputStream().use { input ->
            assertEquals(4, input.read(tag))
        }
        assertEquals("OTTO", String(tag, Charsets.US_ASCII))
        assertEquals(CandidateFont.ASSET, "fonts/${font.name}")
        val ofl = File(font.parentFile, "OFL.txt")
        assertTrue(ofl.readText().contains("SIL OPEN FONT LICENSE"))
    }
}
