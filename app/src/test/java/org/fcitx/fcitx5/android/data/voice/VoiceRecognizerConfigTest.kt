/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.voice

import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecognizerConfigTest {

    @Test
    fun qwenUsesMoreThreadsThanSenseVoiceCeiling() {
        val qwen = VoiceRecognizer.asrThreadCount(VoiceAsrKind.Qwen3)
        val sense = VoiceRecognizer.asrThreadCount(VoiceAsrKind.SenseVoice)
        assertTrue("qwen threads=$qwen", qwen in 2..4)
        assertTrue("sense threads=$sense", sense in 1..2)
        assertTrue(qwen >= sense)
    }
}
