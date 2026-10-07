/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import timber.log.Timber

/**
 * 离线识别封装（sherpa-onnx）。按设置加载 SenseVoice-Small 或 Qwen3-ASR-0.6B。
 * 首次识别时加载模型；输入窗口关闭时调用 [release] 释放内存。
 * 所有方法线程安全；识别在调用线程上同步执行。
 */
object VoiceRecognizer {

    private const val SAMPLE_RATE = 16000

    private var recognizer: OfflineRecognizer? = null

    /** 当前识别器对应的引擎；null = 未加载。换引擎必须重建。 */
    private var loadedKind: VoiceAsrKind? = null

    /** SenseVoice 所用语言码；Qwen3 自带语种检测，此项忽略。 */
    private var loadedLanguage: String? = null

    val isLoaded: Boolean
        @Synchronized get() = recognizer != null

    /**
     * 按需加载当前选中的模型；文件缺失时返回 false。
     * SenseVoice 换识别语言会重建识别器；Qwen3 只在换引擎时重建。
     */
    @Synchronized
    fun load(): Boolean {
        val kind = AppPrefs.getInstance().keyboard.voiceAsrKind.getValue()
        val language = AppPrefs.getInstance().keyboard.voiceLanguage.getValue().code
        if (recognizer != null) {
            val sameKind = loadedKind == kind
            val languageOk = kind != VoiceAsrKind.SenseVoice || loadedLanguage == language
            if (sameKind && languageOk) return true
            release()
        }
        if (!VoiceModelManager.isReady(kind)) return false
        return try {
            recognizer = OfflineRecognizer(config = configFor(kind, language))
            loadedKind = kind
            loadedLanguage = language
            true
        } catch (e: Throwable) {
            // OutOfMemoryError 等 Error 也要卸掉半初始化状态，否则 Elite 上 Qwen3 一崩就再也起不来
            Timber.e(e, "failed to load voice model kind=%s", kind)
            release()
            false
        }
    }

    private fun configFor(kind: VoiceAsrKind, language: String): OfflineRecognizerConfig {
        val modelConfig = when (kind) {
            VoiceAsrKind.SenseVoice -> OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = VoiceModelManager.modelFile.absolutePath,
                    language = language,
                    useInverseTextNormalization = true,
                ),
                tokens = VoiceModelManager.tokensFile.absolutePath,
                numThreads = 2,
            )
            VoiceAsrKind.Qwen3 -> OfflineModelConfig(
                qwen3Asr = OfflineQwen3AsrModelConfig(
                    convFrontend = VoiceModelManager.qwenConvFrontendFile.absolutePath,
                    encoder = VoiceModelManager.qwenEncoderFile.absolutePath,
                    decoder = VoiceModelManager.qwenDecoderFile.absolutePath,
                    tokenizer = VoiceModelManager.qwenTokenizerDir.absolutePath,
                    maxNewTokens = 256,
                ),
                tokens = "",
                numThreads = 2,
            )
        }
        return OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )
    }

    /**
     * 识别一段 16kHz 单声道 float PCM（幅度范围 [-1, 1]），返回带标点的文本。
     * 未加载且加载失败时返回 null。
     */
    @Synchronized
    fun recognize(samples: FloatArray): String? {
        if (!load()) return null
        if (samples.isEmpty()) return ""
        val kind = loadedKind ?: return null
        return try {
            val rec = recognizer!!
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                rec.decode(stream)
                VoiceAsrTranscript.clean(kind, rec.getResult(stream).text)
            } finally {
                stream.release()
            }
        } catch (e: Throwable) {
            Timber.e(e, "voice recognition failed kind=%s", kind)
            if (e is OutOfMemoryError) release()
            null
        }
    }

    @Synchronized
    fun release() {
        recognizer?.release()
        recognizer = null
        loadedKind = null
        loadedLanguage = null
    }
}
