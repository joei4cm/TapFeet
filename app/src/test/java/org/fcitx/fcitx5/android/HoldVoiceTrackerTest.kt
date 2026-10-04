/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.swipe.HoldVoiceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldVoiceTrackerTest {

    @Test
    fun stillHoldStartsThenLiftStops() {
        val t = HoldVoiceTracker()
        t.down()
        assertEquals(HoldVoiceTracker.Command.Start, t.elapsed())
        assertTrue(t.started)
        assertEquals(HoldVoiceTracker.Command.Stop, t.up())
        assertFalse(t.started)
    }

    @Test
    fun swipeBeforeHoldCancelsPending() {
        val t = HoldVoiceTracker()
        t.down()
        assertEquals(HoldVoiceTracker.Command.None, t.cancel())
        assertEquals(HoldVoiceTracker.Command.None, t.elapsed())
        assertEquals(HoldVoiceTracker.Command.None, t.up())
    }

    @Test
    fun liftBeforeHoldDoesNothing() {
        val t = HoldVoiceTracker()
        t.down()
        assertEquals(HoldVoiceTracker.Command.None, t.up())
    }

    @Test
    fun holdIsOneSecondNotARest() {
        assertEquals(1000L, HoldVoiceTracker.HOLD_MS)
    }
}
