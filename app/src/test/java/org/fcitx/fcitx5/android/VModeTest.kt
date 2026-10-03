/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.vmode.VMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class VModeTest {

    @Test
    fun ignoresPinyinNv() {
        assertFalse(VMode.isActive("nv"))
        assertFalse(VMode.isActive("lv"))
        assertFalse(VMode.isActive("van"))
        assertFalse(VMode.isActive(""))
    }

    @Test
    fun activatesOnVAndDigitsOrMath() {
        assertTrue(VMode.isActive("v"))
        assertTrue(VMode.isActive("v123"))
        assertTrue(VMode.isActive("v1+2*3"))
    }

    @Test
    fun chineseNumerals() {
        assertEquals("零", VMode.toChinese("0"))
        assertEquals("一", VMode.toChinese("1"))
        assertEquals("十", VMode.toChinese("10"))
        assertEquals("十一", VMode.toChinese("11"))
        assertEquals("二十", VMode.toChinese("20"))
        assertEquals("一百零一", VMode.toChinese("101"))
        assertEquals("一千", VMode.toChinese("1000"))
        assertEquals("一万", VMode.toChinese("10000"))
        assertEquals("壹佰贰拾叁", VMode.toChineseFinancial("123"))
    }

    @Test
    fun calculator() {
        assertEquals(3.0, VMode.evalMath("1+2")!!, 0.0)
        assertEquals(7.0, VMode.evalMath("1+2*3")!!, 0.0)
        assertEquals(9.0, VMode.evalMath("(1+2)*3")!!, 0.0)
        assertEquals(null, VMode.evalMath("1/0"))
    }

    @Test
    fun dateFromEmptyV() {
        val cal = GregorianCalendar(2026, Calendar.OCTOBER, 3)
        val texts = VMode.suggestions("v", cal).map { it.text }
        assertTrue(texts.contains("2026年10月3日"))
        assertTrue(texts.contains("2026-10-03"))
        assertTrue(texts.contains("星期六"))
    }
}
