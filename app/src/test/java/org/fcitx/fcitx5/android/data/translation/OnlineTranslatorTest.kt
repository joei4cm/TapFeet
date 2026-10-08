/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.translation

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnlineTranslatorTest {

    @Before
    fun setUp() {
        OnlineTranslator.clearCacheForTest()
        OnlineTranslator.fetchOverride = null
    }

    @After
    fun tearDown() {
        OnlineTranslator.fetchOverride = null
        OnlineTranslator.clearCacheForTest()
    }

    @Test
    fun detectLang() {
        assertEquals(OnlineTranslator.Lang.Zh, OnlineTranslator.detectLang("你好世界"))
        assertEquals(OnlineTranslator.Lang.Ja, OnlineTranslator.detectLang("こんにちは"))
        assertEquals(OnlineTranslator.Lang.En, OnlineTranslator.detectLang("hello"))
    }

    @Test
    fun targetsForChinese() {
        assertEquals(
            listOf(OnlineTranslator.Lang.En, OnlineTranslator.Lang.Ja),
            OnlineTranslator.targetsFor(OnlineTranslator.Lang.Zh),
        )
    }

    @Test
    fun parseMyMemoryOk() {
        val body = """{"responseData":{"translatedText":"hello world"},"responseStatus":200}"""
        assertEquals("hello world", OnlineTranslator.parseMyMemory(body))
    }

    @Test
    fun parseMyMemoryWarning() {
        val body =
            """{"responseData":{"translatedText":"MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY"},"responseStatus":200}"""
        assertNull(OnlineTranslator.parseMyMemory(body))
    }

    @Test
    fun parseGoogleOk() {
        val body = """[[["Hello","你好",null,null,10],[" world","世界",null,null,10]],null,"zh-CN"]"""
        assertEquals("Hello world", OnlineTranslator.parseGoogle(body))
    }

    @Test
    fun translateOthersUsesOverride() = runBlocking {
        OnlineTranslator.fetchOverride = { _, to ->
            when (to) {
                OnlineTranslator.Lang.En -> "hello"
                OnlineTranslator.Lang.Ja -> "こんにちは"
                else -> null
            }
        }
        val hits = OnlineTranslator.translateOthers("你好")
        assertEquals(2, hits.size)
        assertEquals("hello", hits.first { it.lang == OnlineTranslator.Lang.En }.text)
        assertEquals("こんにちは", hits.first { it.lang == OnlineTranslator.Lang.Ja }.text)
    }

    @Test
    fun translateOthersSkipsIdentical() = runBlocking {
        OnlineTranslator.fetchOverride = { text, _ -> text }
        assertTrue(OnlineTranslator.translateOthers("日本").isEmpty())
    }
}
