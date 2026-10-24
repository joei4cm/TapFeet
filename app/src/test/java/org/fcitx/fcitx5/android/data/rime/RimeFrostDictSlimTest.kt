/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RimeFrostDictSlimTest {

    @Test
    fun slimKeepsCoreImportsAndDropsCells() {
        val original = """
            ---
            name: rime_frost
            version: "1"
            import_tables:
              - cn_dicts/8105
              - cn_dicts/41448
              - cn_dicts/base
              - cn_dicts/ext
              # - cn_dicts/tencent
              - cn_dicts/others
              - cn_dicts/corrections
              - cn_dicts_cell/game
              - cn_dicts_cell/food

            # 按需启用：
            A	A
        """.trimIndent()
        val text = RimeFrostDictSlim.apply(original)
        assertTrue(RimeFrostDictSlim.changed(original, text))
        assertTrue(text.contains("- cn_dicts/8105"))
        assertTrue(text.contains("- cn_dicts/base"))
        assertTrue(text.contains("- cn_dicts/ext"))
        assertTrue(text.contains("- cn_dicts/others"))
        assertTrue(text.contains("- cn_dicts/corrections"))
        assertFalse(text.contains("cn_dicts_cell"))
        assertFalse(text.contains("41448"))
        assertTrue(text.contains("A\tA"))
    }

    @Test
    fun slimNoOpWhenBlockMissing() {
        val original = "name: x\n"
        val slimmed = RimeFrostDictSlim.apply(original)
        assertEquals(original, slimmed)
        assertFalse(RimeFrostDictSlim.changed(original, slimmed))
    }
}
