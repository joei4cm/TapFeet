/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.voice

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

/**
 * 本地语音识别引擎。两档都走 sherpa-onnx 离线推理，模型按需下载，互不影响。
 *
 * - [SenseVoice]：默认。体积小、CPU 轻，覆盖中英日韩粤。
 * - [Qwen3]：Qwen3-ASR-0.6B int8。更准、语种更多，体积大约 940 MB，Elite 上更慢。
 */
enum class VoiceAsrKind(
    override val stringRes: Int,
    val approxSizeMb: Int,
) : ManagedPreferenceEnum {
    SenseVoice(R.string.voice_asr_kind_sensevoice, 230),
    Qwen3(R.string.voice_asr_kind_qwen3, 940),
}
