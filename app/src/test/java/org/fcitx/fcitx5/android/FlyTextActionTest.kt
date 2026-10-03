/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.swipe.FlyTextAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlyTextActionTest {

    @Test
    fun keepsChannelWithoutCandidates() {
        assertTrue(FlyTextAction.CommitLatinOrDismiss.keepsChannelWithoutCandidates())
        assertTrue(FlyTextAction.SwitchImeNext.keepsChannelWithoutCandidates())
        assertTrue(FlyTextAction.HideBar.keepsChannelWithoutCandidates())
        assertTrue(FlyTextAction.Backspace.keepsChannelWithoutCandidates())
        assertFalse(FlyTextAction.SelectCandidate.keepsChannelWithoutCandidates())
        assertFalse(FlyTextAction.PageNext.keepsChannelWithoutCandidates())
        assertFalse(FlyTextAction.None.keepsChannelWithoutCandidates())
    }
}
