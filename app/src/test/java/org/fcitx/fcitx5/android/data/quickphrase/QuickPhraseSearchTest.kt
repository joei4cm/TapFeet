/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.quickphrase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPhraseSearchTest {

    private val hits = listOf(
        QuickPhraseSearch.Hit("zq", "早上好", "zaoshanghao", "zsh"),
        QuickPhraseSearch.Hit("wx", "微信", "weixin", "wx"),
        QuickPhraseSearch.Hit("ok", "OK", "", ""),
    )

    @Test
    fun emptyQueryReturnsAll() {
        assertEquals(hits, QuickPhraseSearch.filter(hits, ""))
    }

    @Test
    fun fullPinyinFindsChinesePhrase() {
        assertEquals(
            listOf(hits[0]),
            QuickPhraseSearch.filter(hits, "zaoshanghao"),
        )
    }

    @Test
    fun initialsFindChinesePhrase() {
        assertEquals(listOf(hits[0]), QuickPhraseSearch.filter(hits, "zsh"))
    }

    @Test
    fun pinyinPrefixFindsChinesePhrase() {
        assertEquals(listOf(hits[0]), QuickPhraseSearch.filter(hits, "zao"))
    }

    @Test
    fun hanQueryFindsPhrase() {
        assertEquals(listOf(hits[0]), QuickPhraseSearch.filter(hits, "早上"))
    }

    @Test
    fun keywordStillWorks() {
        assertEquals(listOf(hits[1]), QuickPhraseSearch.filter(hits, "wx"))
        assertTrue(QuickPhraseSearch.filter(hits, "ok").any { it.phrase == "OK" })
    }
}
