/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAsrTranscriptTest {

    @Test
    fun qwenStripsLanguagePrefix() {
        assertEquals(
            "你好世界",
            VoiceAsrTranscript.clean(VoiceAsrKind.Qwen3, "language Chinese<asr_text>你好世界"),
        )
        assertEquals(
            "hello",
            VoiceAsrTranscript.clean(VoiceAsrKind.Qwen3, "language English<asr_text>hello"),
        )
        assertEquals(
            "早上好",
            VoiceAsrTranscript.clean(VoiceAsrKind.Qwen3, "Language Mandarin<asr_text>早上好"),
        )
    }

    @Test
    fun qwenEmptyAfterPrefixIsEmpty() {
        assertEquals("", VoiceAsrTranscript.clean(VoiceAsrKind.Qwen3, "language Chinese<asr_text>"))
        assertEquals("", VoiceAsrTranscript.clean(VoiceAsrKind.Qwen3, "  "))
    }

    @Test
    fun senseVoiceKeepsRawText() {
        val raw = "language Chinese<asr_text>不要剥"
        assertEquals(raw, VoiceAsrTranscript.clean(VoiceAsrKind.SenseVoice, raw))
        assertEquals("你好", VoiceAsrTranscript.clean(VoiceAsrKind.SenseVoice, "  你好  "))
    }

    @Test
    fun readyRejectsEmptyOrTinyFiles() {
        assertFalse(VoiceAsrReady.senseVoice(0, 0, 0))
        assertFalse(VoiceAsrReady.senseVoice(1, 1, 1))
        assertTrue(
            VoiceAsrReady.senseVoice(
                VoiceAsrReady.SENSE_VOICE_MODEL_MIN_BYTES,
                VoiceAsrReady.SENSE_VOICE_TOKENS_MIN_BYTES,
                643_854L,
            )
        )
        assertFalse(
            VoiceAsrReady.senseVoice(
                VoiceAsrReady.SENSE_VOICE_MODEL_MIN_BYTES,
                VoiceAsrReady.SENSE_VOICE_TOKENS_MIN_BYTES,
                100L,
            )
        )
        assertFalse(
            VoiceAsrReady.qwen3(
                1, 1, 1, 1, 1, 1, VoiceAsrReady.VAD_MIN_BYTES
            )
        )
        assertTrue(
            VoiceAsrReady.qwen3(
                VoiceAsrReady.QWEN_FRONTEND_MIN_BYTES,
                VoiceAsrReady.QWEN_ENCODER_MIN_BYTES,
                VoiceAsrReady.QWEN_DECODER_MIN_BYTES,
                VoiceAsrReady.QWEN_VOCAB_MIN_BYTES,
                VoiceAsrReady.QWEN_MERGES_MIN_BYTES,
                VoiceAsrReady.QWEN_TOKENIZER_CONFIG_MIN_BYTES,
                VoiceAsrReady.VAD_MIN_BYTES,
            )
        )
    }

    @Test
    fun kindSizesMatchDownloadCopy() {
        assertEquals(230, VoiceAsrKind.SenseVoice.approxSizeMb)
        assertEquals(940, VoiceAsrKind.Qwen3.approxSizeMb)
    }
}
