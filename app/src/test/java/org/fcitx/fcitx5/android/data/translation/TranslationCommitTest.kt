/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.translation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TranslationCommitTest {

    @Before
    fun setUp() {
        TranslationGloss.replaceForTest(
            forward = mapOf(
                "你好" to TranslationGloss.Entry("hello; hi", "こんにちは"),
                "日本" to TranslationGloss.Entry("Japan", "日本"),
            ),
            reverse = mapOf(
                "hello" to "你好",
            ),
        )
    }

    @After
    fun tearDown() {
        TranslationGloss.clearForTest()
    }

    @Test
    fun localItemsChinese() {
        val items = TranslationCommit.localItems("你好", enabled = true)
        assertEquals(
            listOf(
                TranslationCommit.Item("英 hello", "hello"),
                TranslationCommit.Item("日 こんにちは", "こんにちは"),
            ),
            items,
        )
    }

    @Test
    fun localItemsSkipsIdenticalJa() {
        val items = TranslationCommit.localItems("日本", enabled = true)
        assertEquals(listOf(TranslationCommit.Item("英 Japan", "Japan")), items)
    }

    @Test
    fun localItemsEnglishReverse() {
        val items = TranslationCommit.localItems("hello", enabled = true)
        assertEquals(listOf(TranslationCommit.Item("中 你好", "你好")), items)
    }

    @Test
    fun localItemsDisabled() {
        assertEquals(emptyList<TranslationCommit.Item>(), TranslationCommit.localItems("你好", enabled = false))
    }

    @Test
    fun mergePrefersLocal() {
        val local = listOf(TranslationCommit.Item("英 hello", "hello"))
        val online = listOf(
            TranslationCommit.Item("英 Hello", "Hello"),
            TranslationCommit.Item("日 こんにちは", "こんにちは"),
        )
        assertEquals(
            listOf(
                TranslationCommit.Item("英 hello", "hello"),
                TranslationCommit.Item("日 こんにちは", "こんにちは"),
            ),
            TranslationCommit.merge(local, online),
        )
    }
}
