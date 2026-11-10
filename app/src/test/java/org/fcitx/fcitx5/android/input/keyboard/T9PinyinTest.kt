/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9PinyinTest {

    @Test
    fun nihaoFromDigits() {
        assertEquals("nihao", T9Pinyin.decode("64426"))
    }

    @Test
    fun niFrom64() {
        assertEquals("ni", T9Pinyin.decode("64"))
    }

    @Test
    fun incompleteFirstKeyIsInitial() {
        val got = T9Pinyin.decode("6")
        assertTrue("expected n/m/o initial, got $got", got in setOf("n", "m", "o"))
    }

    @Test
    fun woFrom96() {
        assertEquals("wo", T9Pinyin.decode("96"))
    }

    @Test
    fun emptyAndJunk() {
        assertEquals("", T9Pinyin.decode(""))
        assertEquals("", T9Pinyin.decode("1"))
    }

    @Test
    fun t9CodeRoundTripCommon() {
        assertEquals("64", T9Pinyin.t9Code("ni"))
        assertEquals("64426", T9Pinyin.t9Code("nihao"))
        assertEquals("94", T9Pinyin.t9Code("yi"))
        assertEquals("68", T9Pinyin.t9Code("nv"))
    }

    @Test
    fun woainiAndZhongguo() {
        assertEquals("woaini", T9Pinyin.decode("962464"))
        assertEquals("zhongguo", T9Pinyin.decode("94664486"))
    }

    @Test
    fun prefixKeepsTyping() {
        assertEquals("niha", T9Pinyin.decode("6442"))
    }

    @Test
    fun haoDigitBackspaceCanLeaveGaNotHa() {
        // QWERTY "hao" maps to pad 426; dropping the last pad key leaves 42.
        // T9 may re-decode that as "ga" (same pad as "ha"), which must never run on 26-key.
        assertEquals("426", T9Pinyin.digitsOfLatin("hao"))
        assertEquals("42", T9Pinyin.digitsOfLatin("ha"))
        val afterDropO = T9Pinyin.decode("42")
        assertTrue(
            "42 should be a valid incomplete syllable, got $afterDropO",
            afterDropO in setOf("ha", "ga", "h", "g"),
        )
        assertTrue(afterDropO == "ha" || afterDropO == "ga")
    }

    @Test
    fun ganjueBeatsHaojueOnSamePad() {
        assertEquals("426583", T9Pinyin.t9Code("ganjue"))
        assertEquals("426583", T9Pinyin.t9Code("haojue"))
        assertEquals("ganjue", T9Pinyin.decode("426583"))
        assertEquals("ganju", T9Pinyin.decode("42658"))
        assertEquals("ganj", T9Pinyin.decode("4265"))
    }

    @Test
    fun beamKeepsGanjueAmongTop() {
        val top = T9Pinyin.decodeTop("426583", limit = 5)
        assertTrue("ganjue should be in top paths, got $top", "ganjue" in top)
        assertEquals("ganjue", top.first())
    }

    @Test
    fun commonPhrasesStillDecode() {
        assertEquals("ganxie", T9Pinyin.decode(T9Pinyin.t9Code("ganxie")))
        assertEquals("ganga", T9Pinyin.decode(T9Pinyin.t9Code("ganga")))
        assertEquals("hanguo", T9Pinyin.decode(T9Pinyin.t9Code("hanguo")))
        assertEquals("gaoxing", T9Pinyin.decode(T9Pinyin.t9Code("gaoxing")))
        assertEquals("nihao", T9Pinyin.decode(T9Pinyin.t9Code("nihao")))
        assertEquals("xiexie", T9Pinyin.decode(T9Pinyin.t9Code("xiexie")))
    }

    @Test
    fun bareHaoPadStillPrefersHao() {
        assertEquals("hao", T9Pinyin.decode("426"))
    }

    @Test
    fun evalSamePadWordsPreferLexicon() {
        // Regression board: pad collisions that syllable-only DP used to lose.
        val cases = listOf(
            "ganjue" to "感觉",
            "ganxie" to "感谢",
            "ganga" to "尴尬",
            "hanguo" to "韩国",
            "gaoxing" to "高兴",
            "haokan" to "好看",
            "haochi" to "好吃",
            "woaini" to "我爱你",
            "xiexie" to "谢谢",
            "meiyou" to "没有",
        )
        for ((py, label) in cases) {
            val digits = T9Pinyin.t9Code(py)
            assertEquals("$label ($py) digits=$digits", py, T9Pinyin.decode(digits))
        }
    }

    @Test
    fun virtualLayoutNames() {
        assertEquals(TextKeyboard.Name, VirtualLayout.Qwerty.keyboardName)
        assertEquals(NineKeyKeyboard.Name, VirtualLayout.NineKey.keyboardName)
        assertTrue(VirtualLayout.isTextLayout(TextKeyboard.Name))
        assertTrue(VirtualLayout.isTextLayout(NineKeyKeyboard.Name))
        assertFalse(VirtualLayout.isTextLayout("Other"))
    }
}
