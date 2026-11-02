/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.keyboard.KeyHitResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyHitResolverTest {

    // Home row slice: G centered at 360, H at 440, each 80 wide. Geometric boundary is 400.
    private val centers = floatArrayOf(360f, 440f, 520f)
    private val widths = floatArrayOf(80f, 80f, 80f)
    private val forgive = booleanArrayOf(true, true, true)

    @Test
    fun centerOfGStaysG() {
        assertEquals(0, KeyHitResolver.pick(360f, centers, widths, forgive))
    }

    @Test
    fun centerOfHStaysH() {
        assertEquals(1, KeyHitResolver.pick(440f, centers, widths, forgive))
    }

    @Test
    fun tapJustInsideHStillCountsAsG() {
        // Past geometric mid (400); with RIGHT_SIDE_WEIGHT=0.3 the G/H flip is ~421.
        assertEquals(0, KeyHitResolver.pick(410f, centers, widths, forgive))
        assertEquals(0, KeyHitResolver.pick(418f, centers, widths, forgive))
    }

    @Test
    fun tapDeepInsideHIsH() {
        assertEquals(1, KeyHitResolver.pick(430f, centers, widths, forgive))
    }

    @Test
    fun wideKeyDoesNotStealLetterCenter() {
        // Shift (wide, no right-forgive) then Z.
        val c = floatArrayOf(60f, 160f)
        val w = floatArrayOf(120f, 80f)
        val f = booleanArrayOf(false, true)
        assertEquals(1, KeyHitResolver.pick(160f, c, w, f))
        assertEquals(1, KeyHitResolver.pick(130f, c, w, f))
    }

    @Test
    fun rowStaysPutUntilWellIntoNextRow() {
        val height = 400
        val rows = 4
        assertEquals(1, KeyHitResolver.rowIndex(150f, height, rows, stickyRow = 1))
        // Just across the boundary into row 2 (row height 100, 10px in).
        assertEquals(1, KeyHitResolver.rowIndex(210f, height, rows, stickyRow = 1))
        // 30px into the next row clears the 25% margin.
        assertEquals(2, KeyHitResolver.rowIndex(230f, height, rows, stickyRow = 1))
    }

    // T above G above B (same column); H to the right of G — 总/是 等连续拼音的典型上下误触。
    private val centersX2d = floatArrayOf(360f, 360f, 360f, 440f) // T, G, B, H
    private val centersY2d = floatArrayOf(100f, 200f, 300f, 200f)
    private val widths2d = floatArrayOf(80f, 80f, 80f, 80f)
    private val forgive2d = booleanArrayOf(true, true, true, true)

    @Test
    fun verticalNearMissPrefersNearerRow() {
        // Closer to T than G.
        assertEquals(0, KeyHitResolver.pick2D(360f, 130f, centersX2d, centersY2d, widths2d, forgive2d))
        // Closer to G than T.
        assertEquals(1, KeyHitResolver.pick2D(360f, 170f, centersX2d, centersY2d, widths2d, forgive2d))
        // Closer to G than B.
        assertEquals(1, KeyHitResolver.pick2D(360f, 230f, centersX2d, centersY2d, widths2d, forgive2d))
    }

    @Test
    fun twoDStillForgivesRightThumbOnG() {
        // Slightly right of G and a bit toward T — still G, not H.
        assertEquals(1, KeyHitResolver.pick2D(410f, 190f, centersX2d, centersY2d, widths2d, forgive2d))
    }
}
