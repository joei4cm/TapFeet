/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.translation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TranslationGlossTest {

    @Before
    fun setUp() {
        TranslationGloss.replaceForTest(
            forward = mapOf(
                "你好" to TranslationGloss.Entry("hello; hi", "こんにちは"),
                "日本" to TranslationGloss.Entry("Japan", "日本"),
                "こんにちは" to TranslationGloss.Entry("hello", null),
            ),
            reverse = mapOf(
                "hello" to "你好",
                "japan" to "日本",
            ),
        )
    }

    @After
    fun tearDown() {
        TranslationGloss.clearForTest()
    }

    @Test
    fun entryOfChineseHasEnAndJa() {
        val e = TranslationGloss.entryOf("你好")!!
        assertEquals("hello; hi", e.en)
        assertEquals("こんにちは", e.ja)
    }

    @Test
    fun glossOfChinese() {
        assertEquals("hello; hi", TranslationGloss.glossOf("你好"))
        assertEquals("Japan", TranslationGloss.glossOf("日本"))
    }

    @Test
    fun glossOfJapanese() {
        assertEquals("hello", TranslationGloss.glossOf("こんにちは"))
    }

    @Test
    fun glossPrefixFallback() {
        assertEquals("hello; hi", TranslationGloss.glossOf("你好呀")?.let {
            TranslationGloss.entryOf("你好呀")!!.en
        })
    }

    @Test
    fun reverseEnglish() {
        assertEquals("你好", TranslationGloss.sourceOfEnglish("Hello"))
        assertEquals("日本", TranslationGloss.sourceOfEnglish("japan"))
        assertNull(TranslationGloss.sourceOfEnglish("zzzz"))
    }
}
