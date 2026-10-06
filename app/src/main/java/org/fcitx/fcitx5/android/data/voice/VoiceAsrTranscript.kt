/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.voice

/**
 * 识别结果后处理。Qwen3-ASR 会在正文前加 `language Chinese<asr_text>` 这类标记，上屏前剥掉。
 */
object VoiceAsrTranscript {

    private val qwenLanguagePrefix = Regex("""(?i)^language\s+\S+<asr_text>""")

    fun clean(kind: VoiceAsrKind, text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        return if (kind == VoiceAsrKind.Qwen3) {
            trimmed.replaceFirst(qwenLanguagePrefix, "").trim()
        } else {
            trimmed
        }
    }
}
