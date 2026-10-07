/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
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

    @Test
    fun softGainBoostsQuietSpeech() {
        val peak = 0.05f
        val gain = VoiceCaptureSupport.softGain(peak)
        assertEquals(VoiceCaptureSupport.TARGET_PEAK / peak, gain, 1e-4f)
        assertTrue(gain > 1f)
        assertTrue(gain <= VoiceCaptureSupport.MAX_GAIN)
    }

    @Test
    fun softGainSkipsSilenceAndLoud() {
        assertEquals(1f, VoiceCaptureSupport.softGain(0f), 0f)
        assertEquals(1f, VoiceCaptureSupport.softGain(VoiceCaptureSupport.SILENCE_PEAK / 2), 0f)
        assertEquals(1f, VoiceCaptureSupport.softGain(VoiceCaptureSupport.TARGET_PEAK), 0f)
        assertEquals(1f, VoiceCaptureSupport.softGain(0.9f), 0f)
    }

    @Test
    fun softGainCapsAtMax() {
        val tiny = VoiceCaptureSupport.SILENCE_PEAK + 1e-5f
        assertEquals(VoiceCaptureSupport.MAX_GAIN, VoiceCaptureSupport.softGain(tiny), 1e-3f)
    }
}
