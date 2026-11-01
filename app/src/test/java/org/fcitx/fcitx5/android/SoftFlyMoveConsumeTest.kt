/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.swipe.SWIPE_BASE_SLOP_DP
import org.fcitx.fcitx5.android.input.swipe.SWIPE_PAGE_SLOP_DP
import org.fcitx.fcitx5.android.input.swipe.SWIPE_UP_SLOP_DP
import org.fcitx.fcitx5.android.input.swipe.SwipeDirection
import org.fcitx.fcitx5.android.input.swipe.softFlyMoveConsumesClick
import org.fcitx.fcitx5.android.input.swipe.swipeAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftFlyMoveConsumeTest {

    private val density = 3f

    @Test
    fun microUpDriftDoesNotKillLetterClick() {
        // Between base slop and up commit: axis may lock, click must survive.
        val dy = -((SWIPE_BASE_SLOP_DP + SWIPE_UP_SLOP_DP) / 2f) * density
        assertEquals(SwipeDirection.UP, swipeAxis(0f, dy, density))
        assertFalse(softFlyMoveConsumesClick(0f, dy, density, SwipeDirection.UP))
        assertFalse(softFlyMoveConsumesClick(0f, dy, density, null))
    }

    @Test
    fun committedUpSwipeDoesConsume() {
        val dy = -(SWIPE_UP_SLOP_DP + 1f) * density
        assertTrue(softFlyMoveConsumesClick(0f, dy, density, SwipeDirection.UP))
    }

    @Test
    fun committedHorizontalPageDoesConsume() {
        val dx = -(SWIPE_PAGE_SLOP_DP + 1f) * density
        assertTrue(softFlyMoveConsumesClick(dx, 0f, density, SwipeDirection.LEFT))
    }

    @Test
    fun downNeverConsumesForSoftFly() {
        val dy = (SWIPE_UP_SLOP_DP + 1f) * density
        assertFalse(softFlyMoveConsumesClick(0f, dy, density, SwipeDirection.DOWN))
        assertFalse(softFlyMoveConsumesClick(0f, dy, density, null))
    }
}
