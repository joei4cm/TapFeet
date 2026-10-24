/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.rime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RimeSchemaSwitcherTest {

    @Test
    fun customYamlPointsAtIce() {
        val yaml = RimeSchemaSwitcher.customYaml(RimeSchemaSwitcher.RIME_ICE)
        assertTrue(yaml.contains("schema: rime_ice"))
        assertTrue(yaml.contains("Shift_L: noop"))
        assertFalse(yaml.contains("commit_code"))
    }

    @Test
    fun customYamlPointsAtLuna() {
        val yaml = RimeSchemaSwitcher.customYaml(RimeSchemaSwitcher.LUNA_SIMP)
        assertTrue(yaml.contains("schema: luna_pinyin_simp"))
        assertTrue(yaml.contains("page_size: 5"))
    }
}
