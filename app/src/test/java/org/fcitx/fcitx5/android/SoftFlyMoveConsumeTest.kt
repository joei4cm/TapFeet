/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.swipe.SOFT_FLY_BASE_SLOP_DP
import org.fcitx.fcitx5.android.input.swipe.SOFT_FLY_PAGE_SLOP_DP
import org.fcitx.fcitx5.android.input.swipe.SOFT_FLY_UP_SLOP_DP
import org.fcitx.fcitx5.android.input.swipe.SWIPE_UP_SLOP_DP
import org.fcitx.fcitx5.android.input.swipe.SwipeDirection
import org.fcitx.fcitx5.android.input.swipe.softFlyAxis
import org.fcitx.fcitx5.android.input.swipe.softFlyDirection
import org.fcitx.fcitx5.android.input.swipe.softFlyMoveConsumesClick
import org.fcitx.fcitx5.android.input.swipe.swipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftFlyMoveConsumeTest {

    private val density = 3f

    @Test
    fun softUpSlopIsStricterThanPhysical() {
        assertTrue(SOFT_FLY_UP_SLOP_DP > SWIPE_UP_SLOP_DP)
        // Clears physical up slop but not soft — must not consume / not commit soft fly.
        val dy = -(SWIPE_UP_SLOP_DP + 2f) * density
        assertNull(softFlyDirection(0f, dy, density))
        assertFalse(softFlyMoveConsumesClick(0f, dy, density, SwipeDirection.UP))
        assertEquals(SwipeDirection.UP, swipeDirection(0f, dy, density))
    }

    @Test
    fun microUpDriftDoesNotKillLetterClick() {
        val dy = -((SOFT_FLY_BASE_SLOP_DP + SOFT_FLY_UP_SLOP_DP) / 2f) * density
        assertEquals(SwipeDirection.UP, softFlyAxis(0f, dy, density))
        assertFalse(softFlyMoveConsumesClick(0f, dy, density, SwipeDirection.UP))
        assertFalse(softFlyMoveConsumesClick(0f, dy, density, null))
    }

    @Test
    fun committedSoftUpSwipeDoesConsume() {
        val dy = -(SOFT_FLY_UP_SLOP_DP + 1f) * density
        assertEquals(SwipeDirection.UP, softFlyDirection(0f, dy, density))
        assertTrue(softFlyMoveConsumesClick(0f, dy, density, SwipeDirection.UP))
    }

    @Test
    fun committedSoftHorizontalPageDoesConsume() {
        val dx = -(SOFT_FLY_PAGE_SLOP_DP + 1f) * density
        assertTrue(softFlyMoveConsumesClick(dx, 0f, density, SwipeDirection.LEFT))
    }

    @Test
    fun diagonalDoesNotLockSoftAxis() {
        // Nearly 45° — softer axis ratio 2.0 should reject.
        val dy = -40f * density
        val dx = 30f * density
        assertNull(softFlyAxis(dx, dy, density))
    }

    @Test
    fun downNeverConsumesForSoftFly() {
        val dy = (SOFT_FLY_UP_SLOP_DP + 1f) * density
        assertFalse(softFlyMoveConsumesClick(0f, dy, density, SwipeDirection.DOWN))
        assertFalse(softFlyMoveConsumesClick(0f, dy, density, null))
    }
}
