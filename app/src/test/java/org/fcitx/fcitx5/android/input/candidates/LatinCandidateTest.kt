/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.candidates

import org.fcitx.fcitx5.android.core.CandidateWord
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatinCandidateTest {

    @Test
    fun chineseImesHideEnglishWords() {
        assertTrue(LatinCandidate.hidesInIme("pinyin"))
        assertTrue(LatinCandidate.hidesInIme("shuangpin"))
        assertTrue(LatinCandidate.hidesInIme("rime"))
        assertTrue(LatinCandidate.hidesInIme("wbx"))
        assertFalse(LatinCandidate.hidesInIme("keyboard-us"))
        assertFalse(LatinCandidate.hidesInIme(""))
    }

    @Test
    fun englishWordIsPureLatin() {
        assertTrue(LatinCandidate.isEnglishWord("hello"))
        assertTrue(LatinCandidate.isEnglishWord("nihao"))
        assertTrue(LatinCandidate.isEnglishWord("OK"))
        assertTrue(LatinCandidate.isEnglishWord("don't"))
        assertTrue(LatinCandidate.isEnglishWord("e-mail"))
        assertFalse(LatinCandidate.isEnglishWord(""))
        assertFalse(LatinCandidate.isEnglishWord("你"))
        assertFalse(LatinCandidate.isEnglishWord("你好"))
        assertFalse(LatinCandidate.isEnglishWord("WiFi密码"))
        assertFalse(LatinCandidate.isEnglishWord("123"))
        assertFalse(LatinCandidate.isEnglishWord(":)"))
        assertFalse(LatinCandidate.isEnglishWord("-hello"))
        assertFalse(LatinCandidate.isEnglishWord("'hello"))
    }

    @Test
    fun dropKeepsEngineIndexOfChineseWords() {
        val words = arrayOf(
            word("nice"),
            word("你"),
            word("night"),
            word("呢"),
        )
        val dropped = LatinCandidate.drop(words, hideEnglish = true, indexBase = 4)
        assertEquals(listOf("你", "呢"), dropped.words.map { it.text })
        assertArrayEquals(intArrayOf(5, 7), dropped.engineIndices)
    }

    @Test
    fun dropIsIdentityWhenEnglishIme() {
        val words = arrayOf(word("the"), word("there"))
        val dropped = LatinCandidate.drop(words, hideEnglish = false)
        assertEquals(2, dropped.words.size)
        assertArrayEquals(intArrayOf(0, 1), dropped.engineIndices)
    }

    @Test
    fun displayCursorSkipsHiddenEnglish() {
        val indices = intArrayOf(1, 3)
        assertEquals(0, LatinCandidate.displayCursor(1, indices))
        assertEquals(1, LatinCandidate.displayCursor(3, indices))
        assertEquals(0, LatinCandidate.displayCursor(0, indices))
        assertEquals(0, LatinCandidate.displayCursor(-1, indices))
        assertEquals(-1, LatinCandidate.displayCursor(0, intArrayOf()))
    }

    private fun word(text: String) = CandidateWord("", text, "")
}
