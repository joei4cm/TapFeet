/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.data.CandidateFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateFrequencyTest {

    @Test
    fun grade_unknownIsNone() {
        assertEquals(CandidateFrequency.Grade.None, CandidateFrequency.grade(0))
        assertEquals(CandidateFrequency.Grade.None, CandidateFrequency.grade(-1))
    }

    @Test
    fun grade_stepsUpWithCount() {
        assertEquals(CandidateFrequency.Grade.Soft, CandidateFrequency.grade(1))
        assertEquals(CandidateFrequency.Grade.Soft, CandidateFrequency.grade(2))
        assertEquals(CandidateFrequency.Grade.Medium, CandidateFrequency.grade(3))
        assertEquals(CandidateFrequency.Grade.Medium, CandidateFrequency.grade(7))
        assertEquals(CandidateFrequency.Grade.Strong, CandidateFrequency.grade(8))
        assertEquals(CandidateFrequency.Grade.Strong, CandidateFrequency.grade(19))
        assertEquals(CandidateFrequency.Grade.Peak, CandidateFrequency.grade(20))
        assertEquals(CandidateFrequency.Grade.Peak, CandidateFrequency.grade(100))
    }

    @Test
    fun blendFactor_unknownIsZero() {
        assertEquals(0f, CandidateFrequency.blendFactor(0), 0f)
        assertEquals(0f, CandidateFrequency.blendFactor(-1), 0f)
    }

    @Test
    fun blendFactor_discreteLadder() {
        val soft = CandidateFrequency.blendFactor(1)
        val medium = CandidateFrequency.blendFactor(5)
        val strong = CandidateFrequency.blendFactor(10)
        val peak = CandidateFrequency.blendFactor(50)
        assertTrue(soft < medium)
        assertTrue(medium < strong)
        assertTrue(strong < peak)
        // Same band shares one factor (not a continuous curve).
        assertEquals(soft, CandidateFrequency.blendFactor(2), 0f)
        assertEquals(medium, CandidateFrequency.blendFactor(7), 0f)
        assertEquals(0.88f, peak, 0f)
    }

    @Test
    fun colorFor_unknownKeepsBase() {
        val base = 0xFFEEEEEE.toInt()
        val accent = 0xFF448AFF.toInt()
        assertEquals(base, CandidateFrequency.colorFor(0, base, accent))
    }

    @Test
    fun colorFor_gradesTowardAccentNotWhite() {
        // Dark-theme candidate text + blue accent (never blend toward white foreground).
        val base = 0xFFFAFAFA.toInt()
        val accent = 0xFF5E97F6.toInt()
        val soft = CandidateFrequency.colorFor(1, base, accent)
        val peak = CandidateFrequency.colorFor(50, base, accent)
        assertNotEquals(base, soft)
        assertNotEquals(base, peak)
        assertNotEquals(soft, peak)
        // Peak must be closer to accent than soft (blue channel rises as we blend).
        val softB = soft and 0xFF
        val peakB = peak and 0xFF
        val accentB = accent and 0xFF
        assertTrue(kotlin.math.abs(peakB - accentB) < kotlin.math.abs(softB - accentB))
    }
}
