/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.quickphrase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPhrasePinyinAliasTest {

    private val pinyin = mapOf(
        "早上好" to "zaoshanghao",
        "微信" to "weixin",
        "你好" to "nihao",
    )
    private val initials = mapOf(
        "早上好" to "zsh",
        "微信" to "wx",
        "你好" to "nh",
    )

    private fun extra(vararg entries: QuickPhraseEntry) = QuickPhrasePinyinAlias.aliasEntries(
        entries.toList(),
        pinyinOf = pinyin::get,
        initialsOf = initials::get,
    )

    @Test
    fun addsFullPinyinAndInitialsForChinesePhrase() {
        val got = extra(QuickPhraseEntry("zq", "早上好"))
        assertEquals(
            listOf(
                QuickPhraseEntry("zaoshanghao", "早上好"),
                QuickPhraseEntry("zsh", "早上好"),
            ),
            got,
        )
    }

    @Test
    fun skipsAliasThatDuplicatesTheKeyword() {
        val got = extra(QuickPhraseEntry("zaoshanghao", "早上好"))
        assertEquals(listOf(QuickPhraseEntry("zsh", "早上好")), got)
    }

    @Test
    fun skipsNonHanPhrases() {
        assertTrue(extra(QuickPhraseEntry("smile", "(^_^)")).isEmpty())
    }

    @Test
    fun aliasesChineseKeyword() {
        val got = extra(QuickPhraseEntry("微信", "wechat.com"))
        assertEquals(
            listOf(
                QuickPhraseEntry("weixin", "wechat.com"),
                QuickPhraseEntry("wx", "wechat.com"),
            ),
            got,
        )
    }
}
