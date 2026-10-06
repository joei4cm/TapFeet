/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.voice

/**
 * 用体积门槛判断模型文件是否真的下完，避免空文件 / 下到一半被当成 Ready。
 */
object VoiceAsrReady {

    const val SENSE_VOICE_MODEL_MIN_BYTES = 200L * 1024 * 1024
    const val SENSE_VOICE_TOKENS_MIN_BYTES = 1024L
    const val VAD_MIN_BYTES = 1024L * 1024

    const val QWEN_FRONTEND_MIN_BYTES = 30L * 1024 * 1024
    const val QWEN_ENCODER_MIN_BYTES = 150L * 1024 * 1024
    const val QWEN_DECODER_MIN_BYTES = 600L * 1024 * 1024
    const val QWEN_VOCAB_MIN_BYTES = 1024L * 1024
    const val QWEN_MERGES_MIN_BYTES = 512L * 1024
    const val QWEN_TOKENIZER_CONFIG_MIN_BYTES = 1024L

    fun senseVoice(modelBytes: Long, tokensBytes: Long, vadBytes: Long): Boolean {
        return modelBytes >= SENSE_VOICE_MODEL_MIN_BYTES &&
            tokensBytes >= SENSE_VOICE_TOKENS_MIN_BYTES &&
            vadBytes >= VAD_MIN_BYTES
    }

    fun qwen3(
        frontendBytes: Long,
        encoderBytes: Long,
        decoderBytes: Long,
        vocabBytes: Long,
        mergesBytes: Long,
        tokenizerConfigBytes: Long,
        vadBytes: Long,
    ): Boolean {
        return frontendBytes >= QWEN_FRONTEND_MIN_BYTES &&
            encoderBytes >= QWEN_ENCODER_MIN_BYTES &&
            decoderBytes >= QWEN_DECODER_MIN_BYTES &&
            vocabBytes >= QWEN_VOCAB_MIN_BYTES &&
            mergesBytes >= QWEN_MERGES_MIN_BYTES &&
            tokenizerConfigBytes >= QWEN_TOKENIZER_CONFIG_MIN_BYTES &&
            vadBytes >= VAD_MIN_BYTES
    }
}
