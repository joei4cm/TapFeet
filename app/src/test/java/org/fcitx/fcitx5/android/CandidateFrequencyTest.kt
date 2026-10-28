/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.data.CandidateFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateFrequencyTest {

    @Test
    fun blendFactor_unknownIsZero() {
        assertEquals(0f, CandidateFrequency.blendFactor(0), 0f)
        assertEquals(0f, CandidateFrequency.blendFactor(-1), 0f)
    }

    @Test
    fun blendFactor_increasesWithCount() {
        val a = CandidateFrequency.blendFactor(1)
        val b = CandidateFrequency.blendFactor(5)
        val c = CandidateFrequency.blendFactor(50)
        assertTrue(a in 0.28f..0.88f)
        assertTrue(b > a)
        assertTrue(c >= b)
        assertTrue(c <= 0.88f)
    }

    @Test
    fun colorFor_unknownKeepsBase() {
        val base = 0xFFEEEEEE.toInt()
        val accent = 0xFF448AFF.toInt()
        assertEquals(base, CandidateFrequency.colorFor(0, base, accent))
    }
}
