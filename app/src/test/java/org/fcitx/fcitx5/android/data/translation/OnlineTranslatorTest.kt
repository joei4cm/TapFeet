/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.translation

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(OnlineTranslator.Lang.Ko, OnlineTranslator.detectLang("안녕하세요"))
        assertEquals(OnlineTranslator.Lang.En, OnlineTranslator.detectLang("hello"))
        assertEquals(OnlineTranslator.Lang.Ru, OnlineTranslator.detectLang("привет"))
    }

    @Test
    fun targetsForChineseDefault() {
        assertEquals(
            listOf(OnlineTranslator.Lang.En, OnlineTranslator.Lang.Ja),
            OnlineTranslator.targetsFor(OnlineTranslator.Lang.Zh),
        )
    }

    @Test
    fun targetsForRespectsSelectionAndSkipsSource() {
        assertEquals(
            listOf(OnlineTranslator.Lang.Ko, OnlineTranslator.Lang.Fr),
            OnlineTranslator.targetsFor(
                OnlineTranslator.Lang.Zh,
                listOf(OnlineTranslator.Lang.Zh, OnlineTranslator.Lang.Ko, OnlineTranslator.Lang.Fr),
            ),
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
    fun isPlausibleRejectsIdenticalAndWrongScript() {
        assertFalse(
            OnlineTranslator.isPlausible("你好", OnlineTranslator.Lang.Zh, OnlineTranslator.Lang.En, "你好")
        )
        assertFalse(
            OnlineTranslator.isPlausible(
                "你好",
                OnlineTranslator.Lang.Zh,
                OnlineTranslator.Lang.En,
                "MYMEMORY WARNING: quota",
            )
        )
        assertFalse(
            OnlineTranslator.isPlausible("hello", OnlineTranslator.Lang.En, OnlineTranslator.Lang.Zh, "hello")
        )
        assertTrue(
            OnlineTranslator.isPlausible("你好", OnlineTranslator.Lang.Zh, OnlineTranslator.Lang.En, "hello")
        )
        assertTrue(
            OnlineTranslator.isPlausible("hello", OnlineTranslator.Lang.En, OnlineTranslator.Lang.Zh, "你好")
        )
    }

    @Test
    fun translateOthersUsesOverride() = runBlocking {
        OnlineTranslator.fetchOverride = { _, to ->
            when (to) {
                OnlineTranslator.Lang.En -> "hello"
                OnlineTranslator.Lang.Ja -> "こんにちは"
                OnlineTranslator.Lang.Ko -> "안녕"
                else -> null
            }
        }
        val hits = OnlineTranslator.translateOthers(
            "你好",
            listOf(OnlineTranslator.Lang.En, OnlineTranslator.Lang.Ja, OnlineTranslator.Lang.Ko),
        )
        assertEquals(3, hits.size)
        assertEquals("hello", hits.first { it.lang == OnlineTranslator.Lang.En }.text)
        assertEquals("こんにちは", hits.first { it.lang == OnlineTranslator.Lang.Ja }.text)
        assertEquals("안녕", hits.first { it.lang == OnlineTranslator.Lang.Ko }.text)
    }

    @Test
    fun translateOthersSkipsIdentical() = runBlocking {
        OnlineTranslator.fetchOverride = { text, _ -> text }
        assertTrue(
            OnlineTranslator.translateOthers(
                "日本",
                listOf(OnlineTranslator.Lang.En, OnlineTranslator.Lang.Ja),
            ).isEmpty()
        )
    }
}
