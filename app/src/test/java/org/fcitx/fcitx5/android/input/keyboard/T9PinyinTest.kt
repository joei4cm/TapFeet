/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
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
    fun virtualLayoutNames() {
        assertEquals(TextKeyboard.Name, VirtualLayout.Qwerty.keyboardName)
        assertEquals(NineKeyKeyboard.Name, VirtualLayout.NineKey.keyboardName)
        assertTrue(VirtualLayout.isTextLayout(TextKeyboard.Name))
        assertTrue(VirtualLayout.isTextLayout(NineKeyKeyboard.Name))
    }
}
