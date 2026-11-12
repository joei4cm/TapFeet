/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftPinyinTypoTest {

    @Test
    fun exactInputUnchanged() {
        assertEquals("nihao", SoftPinyinTypo.recover("nihao"))
        assertEquals("zhongguo", SoftPinyinTypo.recover("zhongguo"))
        assertEquals("weixin", SoftPinyinTypo.recover("weixin"))
        assertEquals("ganjue", SoftPinyinTypo.recover("ganjue"))
    }

    @Test
    fun transposeRecoversCommonWords() {
        // WowType-style: adjacent letters swapped.
        assertEquals("weixin", SoftPinyinTypo.recover("weixni"))
        assertEquals("zhong", SoftPinyinTypo.recover("zhnog"))
        assertEquals("nihao", SoftPinyinTypo.recover("nhiao"))
    }

    @Test
    fun dropExtraRepeatedKey() {
        assertEquals("hao", SoftPinyinTypo.recover("haoo"))
        assertEquals("nihao", SoftPinyinTypo.recover("nihaoo"))
    }

    @Test
    fun adjacentVerticalCanRecover() {
        // t↔g are vertical neighbors; engine Correction is same-row only.
        val top = SoftPinyinTypo.recoverTop("ghao", limit = 5)
        assertTrue("ghao should consider hao/zhao family, got $top", top.any { it == "hao" || it.startsWith("hao") || it == "zhao" })
    }

    @Test
    fun evalBoardTransposeAndExact() {
        val cases = listOf(
            "nihao" to "nihao",
            "weixni" to "weixin",
            "zhnog" to "zhong",
            "nhiao" to "nihao",
            "haoo" to "hao",
            "zhongguo" to "zhongguo",
            "ganjue" to "ganjue",
            "xiexie" to "xiexie",
        )
        for ((typed, want) in cases) {
            assertEquals("typed=$typed", want, SoftPinyinTypo.recover(typed))
        }
    }

    @Test
    fun softTypoSessionInactiveIgnores() {
        SoftTypoSession.setActive(false)
        SoftTypoSession.clear()
        assertTrue(!SoftTypoSession.hasPending())
    }

    @Test
    fun softTypoSessionTracksWhenActive() {
        SoftTypoSession.setActive(true)
        SoftTypoSession.onPanelLatin("nihao")
        assertTrue(SoftTypoSession.hasPending())
        SoftTypoSession.setActive(false)
        assertTrue(!SoftTypoSession.hasPending())
    }
}
