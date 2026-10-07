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
        val font = fontFile("NotoSansSC-Regular.otf")
        assertTrue(font.length() > 4_000_000L)
        assertEquals("OTTO", readTag(font))
        assertEquals(CandidateFont.ASSET, "fonts/${font.name}")
        val ofl = File(font.parentFile, "OFL.txt")
        assertTrue(ofl.readText().contains("SIL OPEN FONT LICENSE"))
    }

    @Test
    fun plangothicExtCoversRareIdeographAndOfl() {
        val font = fontFile("PlangothicExt-Regular.ttf")
        assertTrue(font.length() > 4_000_000L)
        assertEquals(CandidateFont.EXT_ASSET, "fonts/${font.name}")
        // TrueType scaler type 0x00010000
        val tag = ByteArray(4)
        font.inputStream().use { assertEquals(4, it.read(tag)) }
        assertEquals(0x00.toByte(), tag[0])
        assertEquals(0x01.toByte(), tag[1])
        assertEquals(0x00.toByte(), tag[2])
        assertEquals(0x00.toByte(), tag[3])
        val ofl = File(font.parentFile, "OFL-Plangothic.txt")
        assertTrue(ofl.readText().contains("SIL OPEN FONT LICENSE"))
        val samples = fontFile("PlangothicExt-sample-cps.txt").readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        assertTrue("expected U+2B6A5 in sample list", "2B6A5" in samples)
    }

    private fun fontFile(name: String): File {
        return listOf(
            File("src/main/assets/fonts/$name"),
            File("app/src/main/assets/fonts/$name"),
        ).firstOrNull { it.isFile } ?: error("$name is missing")
    }

    private fun readTag(font: File): String {
        val tag = ByteArray(4)
        font.inputStream().use { input ->
            assertEquals(4, input.read(tag))
        }
        return String(tag, Charsets.US_ASCII)
    }
}
