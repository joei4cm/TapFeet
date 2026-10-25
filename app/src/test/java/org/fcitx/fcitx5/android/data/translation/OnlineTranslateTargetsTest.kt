/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineTranslateTargetsTest {

    @Test
    fun parseDefaultAndAliases() {
        assertEquals(
            listOf(OnlineTranslator.Lang.En, OnlineTranslator.Lang.Ja),
            OnlineTranslateTargets.parse(OnlineTranslateTargets.DEFAULT_RAW),
        )
        assertEquals(
            listOf(OnlineTranslator.Lang.Zh, OnlineTranslator.Lang.Ko),
            OnlineTranslateTargets.parse("zh,ko"),
        )
        assertEquals(
            listOf(OnlineTranslator.Lang.En, OnlineTranslator.Lang.Ja),
            OnlineTranslateTargets.parse(",,,bogus"),
        )
    }

    @Test
    fun encodeRoundTripAndCap() {
        val many = listOf(
            OnlineTranslator.Lang.En,
            OnlineTranslator.Lang.Ja,
            OnlineTranslator.Lang.Ko,
            OnlineTranslator.Lang.Fr,
            OnlineTranslator.Lang.De,
            OnlineTranslator.Lang.Es,
            OnlineTranslator.Lang.Ru,
            OnlineTranslator.Lang.Pt,
        )
        val encoded = OnlineTranslateTargets.encode(many)
        val parsed = OnlineTranslateTargets.parse(encoded)
        assertEquals(OnlineTranslateTargets.MAX_SELECTED, parsed.size)
        assertEquals(OnlineTranslator.Lang.En, parsed.first())
        assertEquals(encoded, OnlineTranslateTargets.encode(parsed))
    }

    @Test
    fun summaryCodes() {
        assertEquals("英 · 日 · 韩", OnlineTranslateTargets.summaryCodes("en,ja,ko"))
    }
}
