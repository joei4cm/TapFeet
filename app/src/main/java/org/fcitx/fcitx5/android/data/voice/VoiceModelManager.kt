/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.voice

import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.io.IOUtils
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.utils.appContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * 管理本地语音输入所需的模型文件。VAD 两档共用；ASR 按 [VoiceAsrKind] 分目录存放。
 *
 *  - SenseVoice：`model.int8.onnx` / `tokens.txt`（约 230MB）
 *  - Qwen3-ASR：`qwen3/{conv_frontend,encoder.int8,decoder.int8,tokenizer/}`（约 940MB）
 *  - `silero_vad.onnx`：静音断句，共用
 *
 * 模型不打进 APK，首次使用按需下载到外部私有目录。
 *
 * 下载源面向国内网络排序：
 *  1. **hf-mirror.com 散文件**（HuggingFace 国内镜像，直下无需解包）；
 *  2. **GitHub Releases tar.bz2** 兜底。
 * ⚠️ 没走 Gitee：免费仓库 Release 单附件上限低于模型体积。
 */
object VoiceModelManager {

    /**
     * 国内镜像散文件（hf-mirror.com）。
     *
     * ⚠️ 必须是 **2024-07-17 官方版** SenseVoiceSmall（int8）。不要图新换成 2025-09-09：
     * 那版是粤语偏好微调（WSYue-ASR），社区实测（sherpa-onnx#2742 / #2746）它**不输出标点**
     * 且开头丢字（「开放」→「放」），整体识别也更差；维护者原话「要粤语才选它，不然不推荐」。
     * 这里用 twmht 镜像仓库（model.int8.onnx / tokens.txt 与官方 tar.bz2 内文件逐字节同尺寸）。
     */
    private const val SENSE_VOICE_MODEL_URL =
        "https://hf-mirror.com/twmht/sherpa-onnx-sense-voice-small" +
            "/resolve/main/model.int8.onnx"

    private const val SENSE_VOICE_TOKENS_URL =
        "https://hf-mirror.com/twmht/sherpa-onnx-sense-voice-small" +
            "/resolve/main/tokens.txt"

    /** GitHub Releases 兜底：tar.bz2 包，内含子目录下的 model.int8.onnx 与 tokens.txt。 */
    private const val SENSE_VOICE_TARBALL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2"

    private const val QWEN_MIRROR_BASE =
        "https://hf-mirror.com/thieunv/sherpa-onnx-qwen3-asr-0.6B-int8/resolve/main"

    private const val QWEN_TARBALL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2"

    private const val VAD_URL =
        "https://hf-mirror.com/R4kSo1997/sherpa-onnx-silero-vad-v5/resolve/main/silero_vad.onnx"

    private const val VAD_FALLBACK_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

    private const val PROGRESS_STEP_BYTES = 256L * 1024L

    sealed class State {
        data object NotDownloaded : State()
        data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : State()
        data object Ready : State()
        data class Error(val message: String) : State()
    }

    val modelDir = File(appContext.getExternalFilesDir(null)!!, "data/voice").also { it.mkdirs() }

    val modelFile = File(modelDir, "model.int8.onnx")
    val tokensFile = File(modelDir, "tokens.txt")
    val vadFile = File(modelDir, "silero_vad.onnx")

    val qwenDir = File(modelDir, "qwen3")
    val qwenConvFrontendFile = File(qwenDir, "conv_frontend.onnx")
    val qwenEncoderFile = File(qwenDir, "encoder.int8.onnx")
    val qwenDecoderFile = File(qwenDir, "decoder.int8.onnx")
    val qwenTokenizerDir = File(qwenDir, "tokenizer")
    val qwenVocabFile = File(qwenTokenizerDir, "vocab.json")
    val qwenMergesFile = File(qwenTokenizerDir, "merges.txt")
    val qwenTokenizerConfigFile = File(qwenTokenizerDir, "tokenizer_config.json")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mainHandler = Handler(Looper.getMainLooper())

    private val mutableState = MutableStateFlow<State>(State.NotDownloaded)
    val state: StateFlow<State>
        get() {
            ensureKindWatch()
            return mutableState
        }

    private var downloadJob: Job? = null
    @Volatile
    private var downloadingKind: VoiceAsrKind? = null

    @Volatile
    private var kindWatchInstalled = false

    @Keep
    private val kindChangeListener = ManagedPreference.OnChangeListener<VoiceAsrKind> { _, kind ->
        VoiceRecognizer.release()
        refreshState(kind)
    }

    // 大文件下载：不设 callTimeout，只约束单次读超时
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun selectedKind(): VoiceAsrKind {
        return AppPrefs.getInstance().keyboard.voiceAsrKind.getValue()
    }

    fun isReady(kind: VoiceAsrKind = selectedKind()): Boolean {
        ensureKindWatch()
        return filesReady(kind)
    }

    fun isDownloadingSelected(): Boolean {
        return downloadJob?.isActive == true && downloadingKind == selectedKind()
    }

    fun isDownloadInProgress(): Boolean = downloadJob?.isActive == true

    fun attachPrefs() {
        ensureKindWatch()
    }

    private fun filesReady(kind: VoiceAsrKind): Boolean {
        val vad = vadFile.length()
        return when (kind) {
            VoiceAsrKind.SenseVoice -> VoiceAsrReady.senseVoice(
                modelFile.length(), tokensFile.length(), vad
            )
            VoiceAsrKind.Qwen3 -> VoiceAsrReady.qwen3(
                qwenConvFrontendFile.length(),
                qwenEncoderFile.length(),
                qwenDecoderFile.length(),
                qwenVocabFile.length(),
                qwenMergesFile.length(),
                qwenTokenizerConfigFile.length(),
                vad,
            )
        }
    }

    private fun ensureKindWatch() {
        if (kindWatchInstalled) return
        synchronized(this) {
            if (kindWatchInstalled) return
            AppPrefs.getInstance().keyboard.voiceAsrKind.registerOnChangeListener(kindChangeListener)
            kindWatchInstalled = true
            refreshState()
        }
    }

    @Synchronized
    fun refreshState(kind: VoiceAsrKind = selectedKind()) {
        if (downloadJob?.isActive == true && downloadingKind == kind) return
        mutableState.value = if (filesReady(kind)) State.Ready else State.NotDownloaded
    }

    /**
     * 确保**当前选中**的模型就绪；缺失时开始下载（幂等，同时只跑一个下载任务）。
     * 进度与结果通过 [state] 发出；可选回调在主线程执行。
     */
    @Synchronized
    fun ensureDownloaded(onSuccess: (() -> Unit)? = null, onFailure: ((String) -> Unit)? = null) {
        ensureKindWatch()
        val kind = selectedKind()
        if (filesReady(kind)) {
            mutableState.value = State.Ready
            onSuccess?.let { mainHandler.post(it) }
            return
        }
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            downloadingKind = kind
            try {
                emitProgress(kind, 0, expectedBytes(kind))
                downloadFirst(
                    listOf(VAD_URL, VAD_FALLBACK_URL),
                    vadFile,
                    VoiceAsrReady.VAD_MIN_BYTES,
                ) { done, total ->
                    emitProgress(kind, done, if (total > 0) total else expectedBytes(kind))
                }
                when (kind) {
                    VoiceAsrKind.SenseVoice -> downloadSenseVoice(kind)
                    VoiceAsrKind.Qwen3 -> downloadQwen3(kind)
                }
                if (!filesReady(kind)) throw IOException("模型文件缺失")
                if (selectedKind() == kind) {
                    mutableState.value = State.Ready
                } else {
                    refreshState()
                }
                onSuccess?.let { mainHandler.post(it) }
            } catch (e: Exception) {
                Timber.e(e, "voice model download failed kind=%s", kind)
                val message = e.message ?: "download failed"
                if (selectedKind() == kind) {
                    mutableState.value = State.Error(message)
                } else {
                    refreshState()
                }
                onFailure?.let { cb -> mainHandler.post { cb(message) } }
            } finally {
                downloadingKind = null
            }
        }
    }

    private fun expectedBytes(kind: VoiceAsrKind): Long = kind.approxSizeMb * 1024L * 1024L

    private fun emitProgress(kind: VoiceAsrKind, downloaded: Long, total: Long) {
        if (selectedKind() == kind) {
            mutableState.value = State.Downloading(downloaded, total)
        }
    }

    private fun existingAsrBytes(kind: VoiceAsrKind): Long {
        return when (kind) {
            VoiceAsrKind.SenseVoice ->
                sizedOrZero(modelFile, VoiceAsrReady.SENSE_VOICE_MODEL_MIN_BYTES) +
                    sizedOrZero(tokensFile, VoiceAsrReady.SENSE_VOICE_TOKENS_MIN_BYTES)
            VoiceAsrKind.Qwen3 ->
                sizedOrZero(qwenConvFrontendFile, VoiceAsrReady.QWEN_FRONTEND_MIN_BYTES) +
                    sizedOrZero(qwenEncoderFile, VoiceAsrReady.QWEN_ENCODER_MIN_BYTES) +
                    sizedOrZero(qwenDecoderFile, VoiceAsrReady.QWEN_DECODER_MIN_BYTES) +
                    sizedOrZero(qwenVocabFile, VoiceAsrReady.QWEN_VOCAB_MIN_BYTES) +
                    sizedOrZero(qwenMergesFile, VoiceAsrReady.QWEN_MERGES_MIN_BYTES) +
                    sizedOrZero(qwenTokenizerConfigFile, VoiceAsrReady.QWEN_TOKENIZER_CONFIG_MIN_BYTES)
        }
    }

    private fun sizedOrZero(file: File, minBytes: Long): Long {
        val n = file.length()
        return if (n >= minBytes) n else 0L
    }

    /**
     * 按源顺序逐个尝试下载到 [dest]；全部失败抛最后一个异常。
     * [dest] 已达到 [minBytes] 时跳过。
     */
    private fun downloadFirst(
        urls: List<String>,
        dest: File,
        minBytes: Long,
        onProgress: ((Long, Long) -> Unit)? = null,
    ) {
        if (dest.length() >= minBytes) return
        dest.delete()
        var lastError: Exception? = null
        for (url in urls) {
            try {
                downloadFile(url, dest, minBytes) { done, total -> onProgress?.invoke(done, total) }
                return
            } catch (e: Exception) {
                Timber.w(e, "download failed, trying next source: $url")
                lastError = e
            }
        }
        throw lastError ?: IOException("no download source for ${dest.name}")
    }

    private fun downloadSenseVoice(kind: VoiceAsrKind) {
        if (filesReady(VoiceAsrKind.SenseVoice)) return
        if (!tryDownloadSenseVoiceFromMirror(kind)) {
            downloadSenseVoiceTarball(kind)
        }
    }

    /** 国内镜像散文件下载 SenseVoice。失败清理残留并返回 false。 */
    private fun tryDownloadSenseVoiceFromMirror(kind: VoiceAsrKind): Boolean {
        return try {
            var doneBase = existingAsrBytes(kind)
            downloadFirst(
                listOf(SENSE_VOICE_MODEL_URL),
                modelFile,
                VoiceAsrReady.SENSE_VOICE_MODEL_MIN_BYTES,
            ) { done, _ ->
                emitProgress(kind, doneBase + done, expectedBytes(kind))
            }
            doneBase = existingAsrBytes(kind)
            downloadFirst(
                listOf(SENSE_VOICE_TOKENS_URL),
                tokensFile,
                VoiceAsrReady.SENSE_VOICE_TOKENS_MIN_BYTES,
            ) { done, _ ->
                emitProgress(kind, doneBase + done, expectedBytes(kind))
            }
            true
        } catch (e: Exception) {
            Timber.w(e, "sense-voice mirror download failed, falling back to tarball")
            File(modelDir, "model.int8.onnx.part").delete()
            File(modelDir, "tokens.txt.part").delete()
            modelFile.delete()
            tokensFile.delete()
            false
        }
    }

    /** GitHub 兜底：下载 tar.bz2 并抽出 SenseVoice 的 model.int8.onnx 与 tokens.txt。 */
    private fun downloadSenseVoiceTarball(kind: VoiceAsrKind) {
        val partModel = File(modelDir, "model.int8.onnx.part")
        val partTokens = File(modelDir, "tokens.txt.part")
        partModel.delete()
        partTokens.delete()
        val request = Request.Builder().url(SENSE_VOICE_TARBALL_URL).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $SENSE_VOICE_TARBALL_URL")
            val body = resp.body ?: throw IOException("empty response body")
            val total = body.contentLength().takeIf { it > 0 } ?: expectedBytes(kind)
            val counted = CountingInputStream(body.byteStream()) { done ->
                emitProgress(kind, done, total)
            }
            BZip2CompressorInputStream(counted).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && name != null) {
                            when {
                                name.endsWith("model.int8.onnx") ->
                                    copyToFile(tar, partModel)
                                name.endsWith("tokens.txt") ->
                                    copyToFile(tar, partTokens)
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
        if (partModel.length() < VoiceAsrReady.SENSE_VOICE_MODEL_MIN_BYTES ||
            partTokens.length() < VoiceAsrReady.SENSE_VOICE_TOKENS_MIN_BYTES
        ) {
            partModel.delete()
            partTokens.delete()
            throw IOException("model archive missing model.int8.onnx / tokens.txt")
        }
        if (!partModel.renameTo(modelFile)) throw IOException("rename model.int8.onnx failed")
        if (!partTokens.renameTo(tokensFile)) throw IOException("rename tokens.txt failed")
    }

    private fun downloadQwen3(kind: VoiceAsrKind) {
        if (filesReady(VoiceAsrKind.Qwen3)) return
        qwenDir.mkdirs()
        qwenTokenizerDir.mkdirs()
        if (!tryDownloadQwenFromMirror(kind)) {
            downloadQwenTarball(kind)
        }
    }

    private fun tryDownloadQwenFromMirror(kind: VoiceAsrKind): Boolean {
        val files = listOf(
            Triple("$QWEN_MIRROR_BASE/conv_frontend.onnx", qwenConvFrontendFile, VoiceAsrReady.QWEN_FRONTEND_MIN_BYTES),
            Triple("$QWEN_MIRROR_BASE/encoder.int8.onnx", qwenEncoderFile, VoiceAsrReady.QWEN_ENCODER_MIN_BYTES),
            Triple("$QWEN_MIRROR_BASE/decoder.int8.onnx", qwenDecoderFile, VoiceAsrReady.QWEN_DECODER_MIN_BYTES),
            Triple("$QWEN_MIRROR_BASE/tokenizer/vocab.json", qwenVocabFile, VoiceAsrReady.QWEN_VOCAB_MIN_BYTES),
            Triple("$QWEN_MIRROR_BASE/tokenizer/merges.txt", qwenMergesFile, VoiceAsrReady.QWEN_MERGES_MIN_BYTES),
            Triple(
                "$QWEN_MIRROR_BASE/tokenizer/tokenizer_config.json",
                qwenTokenizerConfigFile,
                VoiceAsrReady.QWEN_TOKENIZER_CONFIG_MIN_BYTES,
            ),
        )
        return try {
            for ((url, dest, minBytes) in files) {
                val doneBase = existingAsrBytes(kind)
                downloadFirst(listOf(url), dest, minBytes) { done, _ ->
                    emitProgress(kind, doneBase + done, expectedBytes(kind))
                }
            }
            true
        } catch (e: Exception) {
            Timber.w(e, "qwen3 mirror download failed, falling back to tarball")
            deleteQwenPartials()
            qwenConvFrontendFile.delete()
            qwenEncoderFile.delete()
            qwenDecoderFile.delete()
            qwenVocabFile.delete()
            qwenMergesFile.delete()
            qwenTokenizerConfigFile.delete()
            false
        }
    }

    private fun downloadQwenTarball(kind: VoiceAsrKind) {
        qwenDir.mkdirs()
        qwenTokenizerDir.mkdirs()
        val partFrontend = File(qwenDir, "conv_frontend.onnx.part")
        val partEncoder = File(qwenDir, "encoder.int8.onnx.part")
        val partDecoder = File(qwenDir, "decoder.int8.onnx.part")
        val partVocab = File(qwenTokenizerDir, "vocab.json.part")
        val partMerges = File(qwenTokenizerDir, "merges.txt.part")
        val partTokCfg = File(qwenTokenizerDir, "tokenizer_config.json.part")
        listOf(partFrontend, partEncoder, partDecoder, partVocab, partMerges, partTokCfg).forEach { it.delete() }
        val request = Request.Builder().url(QWEN_TARBALL_URL).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $QWEN_TARBALL_URL")
            val body = resp.body ?: throw IOException("empty response body")
            val total = body.contentLength().takeIf { it > 0 } ?: expectedBytes(kind)
            val counted = CountingInputStream(body.byteStream()) { done ->
                emitProgress(kind, done, total)
            }
            BZip2CompressorInputStream(counted).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && name != null) {
                            val dest = qwenTarDest(name, partFrontend, partEncoder, partDecoder, partVocab, partMerges, partTokCfg)
                            if (dest != null) copyToFile(tar, dest)
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
        val extracted = listOf(
            partFrontend to VoiceAsrReady.QWEN_FRONTEND_MIN_BYTES,
            partEncoder to VoiceAsrReady.QWEN_ENCODER_MIN_BYTES,
            partDecoder to VoiceAsrReady.QWEN_DECODER_MIN_BYTES,
            partVocab to VoiceAsrReady.QWEN_VOCAB_MIN_BYTES,
            partMerges to VoiceAsrReady.QWEN_MERGES_MIN_BYTES,
            partTokCfg to VoiceAsrReady.QWEN_TOKENIZER_CONFIG_MIN_BYTES,
        )
        if (extracted.any { it.first.length() < it.second }) {
            extracted.forEach { it.first.delete() }
            throw IOException("qwen3 archive missing required files")
        }
        renameOrThrow(partFrontend, qwenConvFrontendFile)
        renameOrThrow(partEncoder, qwenEncoderFile)
        renameOrThrow(partDecoder, qwenDecoderFile)
        renameOrThrow(partVocab, qwenVocabFile)
        renameOrThrow(partMerges, qwenMergesFile)
        renameOrThrow(partTokCfg, qwenTokenizerConfigFile)
    }

    private fun qwenTarDest(
        name: String,
        frontend: File,
        encoder: File,
        decoder: File,
        vocab: File,
        merges: File,
        tokCfg: File,
    ): File? {
        val normalized = name.replace('\\', '/')
        return when {
            normalized.endsWith("conv_frontend.onnx") -> frontend
            normalized.endsWith("encoder.int8.onnx") -> encoder
            normalized.endsWith("decoder.int8.onnx") -> decoder
            normalized.endsWith("tokenizer/vocab.json") -> vocab
            normalized.endsWith("tokenizer/merges.txt") -> merges
            normalized.endsWith("tokenizer/tokenizer_config.json") -> tokCfg
            else -> null
        }
    }

    private fun deleteQwenPartials() {
        File(qwenDir, "conv_frontend.onnx.part").delete()
        File(qwenDir, "encoder.int8.onnx.part").delete()
        File(qwenDir, "decoder.int8.onnx.part").delete()
        File(qwenTokenizerDir, "vocab.json.part").delete()
        File(qwenTokenizerDir, "merges.txt.part").delete()
        File(qwenTokenizerDir, "tokenizer_config.json.part").delete()
    }

    private fun renameOrThrow(part: File, dest: File) {
        dest.delete()
        if (!part.renameTo(dest)) throw IOException("rename ${dest.name} failed")
    }

    private fun downloadFile(url: String, dest: File, minBytes: Long, onProgress: (Long, Long) -> Unit) {
        if (dest.length() >= minBytes) return
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        part.delete()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $url")
            val body = resp.body ?: throw IOException("empty response body")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            val counted = CountingInputStream(body.byteStream()) { done -> onProgress(done, total) }
            part.outputStream().use { out -> IOUtils.copyLarge(counted, out) }
        }
        if (part.length() < minBytes) {
            part.delete()
            throw IOException("downloaded file too small: $url")
        }
        dest.delete()
        if (!part.renameTo(dest)) throw IOException("rename ${dest.name} failed")
    }

    private fun copyToFile(input: java.io.InputStream, dest: File) {
        dest.parentFile?.mkdirs()
        dest.outputStream().use { out: OutputStream -> IOUtils.copyLarge(input, out) }
    }

    private class CountingInputStream(
        private val delegate: java.io.InputStream,
        private val onBytes: (Long) -> Unit,
    ) : java.io.InputStream() {
        private var count = 0L
        private var lastReport = 0L

        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) bump(1L)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) bump(n.toLong())
            return n
        }

        private fun bump(n: Long) {
            count += n
            if (count - lastReport >= PROGRESS_STEP_BYTES) {
                lastReport = count
                onBytes(count)
            }
        }
    }
}
