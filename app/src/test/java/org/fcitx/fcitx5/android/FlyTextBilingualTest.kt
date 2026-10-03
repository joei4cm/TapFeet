/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.swipe.FlyTextDownAction
import org.fcitx.fcitx5.android.input.swipe.flyTextDownAction
import org.junit.Assert.assertEquals
import org.junit.Test

class FlyTextBilingualTest {

    @Test
    fun pinyinCodesCommitAsLatin() {
        assertEquals(
            FlyTextDownAction.CommitLatin,
            flyTextDownAction("nihao", hasCandidates = true)
        )
    }

    @Test
    fun englishComposingCommitsAsLatinEvenWithoutCandidates() {
        assertEquals(
            FlyTextDownAction.CommitLatin,
            flyTextDownAction("hello", hasCandidates = false)
        )
    }

    @Test
    fun predictionStripIsDismissed() {
        assertEquals(
            FlyTextDownAction.DismissPrediction,
            flyTextDownAction("", hasCandidates = true)
        )
    }

    @Test
    fun idleSurfaceDoesNothing() {
        assertEquals(
            FlyTextDownAction.None,
            flyTextDownAction("", hasCandidates = false)
        )
    }
}
