/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.swipe.FlyTextAction
import org.fcitx.fcitx5.android.input.swipe.FlyTextPaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlyTextPagingTest {

    @Test
    fun defaultLeftIsNextAndRightIsPrev() {
        assertEquals(1, FlyTextPaging.delta(FlyTextAction.PageNext, swap = false))
        assertEquals(-1, FlyTextPaging.delta(FlyTextAction.PagePrev, swap = false))
    }

    @Test
    fun swapMirrorsLeftAndRight() {
        assertEquals(-1, FlyTextPaging.delta(FlyTextAction.PageNext, swap = true))
        assertEquals(1, FlyTextPaging.delta(FlyTextAction.PagePrev, swap = true))
    }

    @Test
    fun ignoresNonPagingActions() {
        assertNull(FlyTextPaging.delta(FlyTextAction.SelectCandidate, swap = false))
        assertNull(FlyTextPaging.delta(FlyTextAction.None, swap = true))
    }
}
