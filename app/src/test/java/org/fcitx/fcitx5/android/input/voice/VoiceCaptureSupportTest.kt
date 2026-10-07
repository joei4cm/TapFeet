/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCaptureSupportTest {

    @Test
    fun nearSilenceBelowThreshold() {
        assertTrue(VoiceCaptureSupport.isNearSilence(0f))
        assertTrue(VoiceCaptureSupport.isNearSilence(VoiceCaptureSupport.SILENCE_PEAK / 2))
        assertTrue(VoiceCaptureSupport.isNearSilence(VoiceCaptureSupport.SILENCE_PEAK - 1e-6f))
    }

    @Test
    fun speechAboveThreshold() {
        assertFalse(VoiceCaptureSupport.isNearSilence(VoiceCaptureSupport.SILENCE_PEAK))
        assertFalse(VoiceCaptureSupport.isNearSilence(0.05f))
        assertFalse(VoiceCaptureSupport.isNearSilence(1f))
    }
}
