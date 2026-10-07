/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.voice.VoiceModelManager
import org.fcitx.fcitx5.android.data.voice.VoiceRecognizer
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 本地语音输入会话控制：
 * 录音（16kHz 单声道 PCM）→ silero-vad 静音断句 → 当前选中的离线 ASR 整段识别。
 *
 * **准流式上屏**：每识别完一段（说话中一个停顿）就把**累积文本**通过 [onPartialText] 发出，
 * 调用方放进编辑器 composing 区 —— 边说边长，用户能看到字在涨；整个会话结束（手动停止 /
 * 静音自动结束）才通过 [onSessionEnd] 交最终文本一次性上屏，空串表示放弃（取消录入）。
 * 两档引擎都是整段模型，这是能做到的最接近流式的体验。
 *
 * [onAudioLevel] 每 0.1s 发一次当前音量（0..1），驱动录音动画。
 * 所有回调均在主线程发出。
 *
 * Titan Elite 等 OEM 上 [MediaRecorder.AudioSource.VOICE_RECOGNITION] 常能初始化却只吐静音，
 * 所以录音源按优先级回退，并在会话前 ~1s 仍近乎静音时热切换到下一个源。
 */
class VoiceInputController(
    context: Context,
    private val onPartialText: (String) -> Unit,
    private val onSessionEnd: (String) -> Unit,
    private val onStateChanged: (State) -> Unit,
    private val onAudioLevel: (Float) -> Unit,
    private val onError: (String) -> Unit,
) {
    enum class State {
        Idle, Recording, Recognizing
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 识别按提交顺序串行执行，保证多段语音的累积次序
    private val recognizeDispatcher = Dispatchers.Default.limitedParallelism(1)

    var state: State = State.Idle
        private set

    @Volatile
    private var recording = false

    /** 本会话累积识别文本（只在 recognizeDispatcher / stop 收尾时写读）。 */
    @Volatile
    private var sessionText = ""

    /**
     * 会话代际：start/destroy 递增。stop 的收尾协程在上屏/改状态前核对自己那代，
     * 防止「切字段后旧会话的识别结果迟到上屏/把状态打回 Idle」的竞态（文字串输入框）。
     */
    @Volatile
    private var sessionId = 0

    /** destroy 置位：空结果不要弹「未检测到声音」。 */
    @Volatile
    private var sessionAborted = false

    /** 本会话读到的峰值幅度（float PCM abs）。 */
    @Volatile
    private var sessionPeakAbs = 0f

    /** 本会话是否有过识别失败（模型加载 / OOM / decode）。 */
    @Volatile
    private var recognizeFailed = false

    // 录音循环复用的转换缓冲与前文缓冲：每 0.1s 的音频不再各 new 一个数组（低端机 GC 抖动）
    private val chunkFloats = FloatArray(SAMPLE_RATE / 10)
    private val prerollBuf = FloatArray(PRE_ROLL_SAMPLES)
    private var prerollLen = 0

    /** 会话结束 60s 无新会话就释放识别器（约 300MB 内存），避免常驻拖累整机流畅度。 */
    private val releaseRecognizerRunnable = Runnable {
        scope.launch { VoiceRecognizer.release() }
    }

    private var recorder: AudioRecord? = null
    private var audioSourceIndex = 0
    private var vad: Vad? = null
    private var captureJob: Job? = null
    private val pendingRecognitions = mutableListOf<Job>()

    /**
     * 后台预加载识别模型（1~3 秒），避免第一次识别卡顿。模型未下载时为空操作。
     */
    fun preload() {
        if (!VoiceModelManager.isReady()) return
        scope.launch { VoiceRecognizer.load() }
    }

    /**
     * 开始录音会话。需要调用方保证已授予 RECORD_AUDIO 权限、模型已下载。
     */
    @Synchronized
    fun start() {
        if (state != State.Idle) return
        if (!VoiceModelManager.isReady()) {
            onError("voice model not ready")
            return
        }
        val opened = try {
            openRecorderFrom(0)
        } catch (e: Exception) {
            Timber.e(e, "failed to create AudioRecord")
            onError(e.message ?: "AudioRecord init failed")
            return
        }
        if (opened == null) {
            onError("AudioRecord not initialized")
            return
        }
        val (rec, sourceIdx) = opened
        val localVad = try {
            Vad(
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = VoiceModelManager.vadFile.absolutePath,
                        threshold = 0.5f,
                        minSilenceDuration = 0.5f,
                        minSpeechDuration = 0.25f,
                        windowSize = 512,
                        maxSpeechDuration = 30f,
                    ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "failed to create Vad")
            releaseRecorder(rec)
            onError(e.message ?: "VAD init failed")
            return
        }
        recorder = rec
        audioSourceIndex = sourceIdx
        vad = localVad
        recording = true
        sessionText = ""
        sessionAborted = false
        sessionPeakAbs = 0f
        recognizeFailed = false
        sessionId += 1
        prerollLen = 0
        mainHandler.removeCallbacks(releaseRecognizerRunnable)
        // 预热识别器：与录音并行，用户说完第一句时通常已就绪
        preload()
        captureJob = scope.launch(Dispatchers.IO) {
            captureLoop(rec, sourceIdx, localVad)
        }
        setState(State.Recording)
    }

    /**
     * 停止录音并识别剩余语音；全部识别完后 [onSessionEnd] 发最终文本，回到 [State.Idle]。
     */
    @Synchronized
    fun stop() {
        if (state != State.Recording) return
        recording = false
        val mySession = sessionId
        setState(State.Recognizing)
        scope.launch(Dispatchers.IO) {
            try {
                captureJob?.join()
            } catch (_: Exception) {
            }
            val localVad = vad
            if (localVad != null) {
                try {
                    localVad.flush()
                    drainVad(localVad)
                } catch (e: Exception) {
                    Timber.e(e, "VAD flush failed")
                }
                localVad.release()
            }
            vad = null
            recorder?.let { releaseRecorder(it) }
            recorder = null
            val pending = synchronized(pendingRecognitions) { pendingRecognitions.toList() }
            pending.forEach { it.join() }
            synchronized(pendingRecognitions) { pendingRecognitions.clear() }
            val text = sessionText
            val peak = sessionPeakAbs
            val failed = recognizeFailed
            val aborted = sessionAborted
            sessionText = ""
            if (mySession == sessionId) {
                mainHandler.post {
                    if (text.isEmpty() && !aborted) {
                        onError(
                            when {
                                VoiceCaptureSupport.isNearSilence(peak) -> ERR_NO_AUDIO
                                failed -> ERR_RECOGNIZE
                                else -> ERR_NO_SPEECH
                            }
                        )
                    }
                    onSessionEnd(text)
                }
                setState(State.Idle)
                // 空闲释放：60s 内没有新会话就把 ~300MB 的识别器放掉
                mainHandler.removeCallbacks(releaseRecognizerRunnable)
                mainHandler.postDelayed(releaseRecognizerRunnable, RECOGNIZER_IDLE_RELEASE_MS)
            }
            // 过期会话（已被 destroy 顶替）：什么都别做，避免迟到上屏/状态回退
        }
    }

    /**
     * 立即终止会话（密码框、输入服务销毁等场景）：丢弃未识别内容，
     * [onSessionEnd] 发空串让调用方清掉 composing 区。
     */
    @Synchronized
    fun destroy() {
        recording = false
        sessionAborted = true
        sessionId += 1 // 顶掉在途的 stop 收尾协程
        captureJob?.cancel()
        captureJob = null
        vad?.let {
            runCatching { it.release() }
        }
        vad = null
        recorder?.let { releaseRecorder(it) }
        recorder = null
        synchronized(pendingRecognitions) { pendingRecognitions.forEach { it.cancel() } }
        synchronized(pendingRecognitions) { pendingRecognitions.clear() }
        prerollLen = 0
        sessionText = ""
        // 识别器释放放到后台：若此刻有识别任务正持有它的锁，主线程等锁会卡顿
        mainHandler.removeCallbacks(releaseRecognizerRunnable)
        scope.launch { VoiceRecognizer.release() }
        mainHandler.post { onSessionEnd("") }
        setState(State.Idle)
    }

    private fun captureLoop(initialRec: AudioRecord, initialSourceIdx: Int, localVad: Vad) {
        val buf = ShortArray(SAMPLE_RATE / 10) // 0.1s
        val voiceAutoStop = AppPrefs.getInstance().keyboard.voiceAutoStop
        val voiceAutoStopSeconds = AppPrefs.getInstance().keyboard.voiceAutoStopSeconds
        var elapsedMs = 0L
        // 距上次语音活动的时刻；0 = 尚未说话（没说话也在计时：开了自动结束就别让会话挂死）
        var lastVoiceMs = 0L
        var currentRec = initialRec
        var sourceIdx = initialSourceIdx
        var triedSilenceFallback = false
        var peakAbs = 0f
        try {
            while (recording) {
                val n = currentRec.read(buf, 0, buf.size)
                if (n <= 0) {
                    Timber.w("AudioRecord.read returned $n source=%d", AUDIO_SOURCES[sourceIdx])
                    // 读失败时立刻换源，别整段会话白录
                    val swapped = swapRecorderIfPossible(currentRec, sourceIdx + 1)
                    if (swapped != null) {
                        currentRec = swapped.first
                        sourceIdx = swapped.second
                        continue
                    }
                    break
                }
                elapsedMs += n * 1000L / SAMPLE_RATE
                // 兜底：单次会话最长 2 分钟，防止录音永不停止
                if (elapsedMs >= MAX_SESSION_MS) {
                    mainHandler.post { stop() }
                    break
                }
                // 就地转换到复用缓冲（不分配）
                for (i in 0 until n) chunkFloats[i] = buf[i] / 32768f
                for (i in 0 until n) {
                    val a = abs(chunkFloats[i])
                    if (a > peakAbs) peakAbs = a
                }
                sessionPeakAbs = peakAbs
                // 音量动画：RMS 映射到 0..1
                var sum = 0f
                for (i in 0 until n) sum += chunkFloats[i] * chunkFloats[i]
                val rms = sqrt(sum / n)
                mainHandler.post { onAudioLevel((rms * 4f).coerceIn(0f, 1f)) }
                // Elite 等机：VOICE_RECOGNITION 初始化成功但只吐近零；约 1s 后热切 MIC
                if (!triedSilenceFallback &&
                    elapsedMs >= SILENCE_FALLBACK_MS &&
                    VoiceCaptureSupport.isNearSilence(peakAbs)
                ) {
                    triedSilenceFallback = true
                    val swapped = swapRecorderIfPossible(currentRec, sourceIdx + 1)
                    if (swapped != null) {
                        Timber.w(
                            "AudioRecord near-silence on source=%d after %dms, switch to %d",
                            AUDIO_SOURCES[sourceIdx],
                            elapsedMs,
                            AUDIO_SOURCES[swapped.second],
                        )
                        currentRec = swapped.first
                        sourceIdx = swapped.second
                        peakAbs = 0f
                        sessionPeakAbs = 0f
                        continue
                    }
                }
                appendPreroll(chunkFloats, n)
                localVad.acceptWaveform(
                    if (n == chunkFloats.size) chunkFloats else chunkFloats.copyOf(n)
                )
                if (localVad.isSpeechDetected()) {
                    lastVoiceMs = elapsedMs
                }
                drainVad(localVad)
                // 静音自动结束：距上次语音活动超过 N 秒就收尾。
                // 走与手动停止同一路径（stop 会 flush VAD），句尾没吐完的语音不会丢。
                if (voiceAutoStop.getValue() &&
                    elapsedMs - lastVoiceMs >= voiceAutoStopSeconds.getValue() * 1000L
                ) {
                    mainHandler.post { stop() }
                    break
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "capture loop failed")
            mainHandler.post { onError(e.message ?: "capture failed") }
        }
    }

    /** 滚动保留最近 [PRE_ROLL_SAMPLES] 个采样的前文（就地搬移，不分配）。 */
    private fun appendPreroll(samples: FloatArray, n: Int) {
        if (n >= PRE_ROLL_SAMPLES) {
            System.arraycopy(samples, n - PRE_ROLL_SAMPLES, prerollBuf, 0, PRE_ROLL_SAMPLES)
            prerollLen = PRE_ROLL_SAMPLES
            return
        }
        if (prerollLen + n > PRE_ROLL_SAMPLES) {
            val keep = PRE_ROLL_SAMPLES - n
            System.arraycopy(prerollBuf, prerollLen - keep, prerollBuf, 0, keep)
            prerollLen = keep
        }
        System.arraycopy(samples, 0, prerollBuf, prerollLen, n)
        prerollLen += n
    }

    /** 取出 VAD 已切出的完整语音段，垫上 0.5s 前文后识别，累积进会话文本并发出预览。 */
    private fun drainVad(localVad: Vad) {
        while (!localVad.empty()) {
            val segment = localVad.front()
            localVad.pop()
            val samples = segment.samples
            if (samples.size < MIN_SEGMENT_SAMPLES) continue
            val pre = if (prerollLen > 0) prerollBuf.copyOf(prerollLen) else FloatArray(0)
            val padded = pre + samples
            val job = scope.launch(recognizeDispatcher) {
                val text = VoiceRecognizer.recognize(padded)
                if (text == null) {
                    recognizeFailed = true
                    return@launch
                }
                if (text.isNotEmpty()) {
                    sessionText += text
                    val snapshot = sessionText
                    mainHandler.post { onPartialText(snapshot) }
                }
            }
            synchronized(pendingRecognitions) { pendingRecognitions.add(job) }
        }
    }

    private fun setState(newState: State) {
        state = newState
        mainHandler.post { onStateChanged(newState) }
    }

    /**
     * 从 [startIndex] 起依次尝试 [AUDIO_SOURCES]，返回第一个能 init+start 的录音器及其下标。
     */
    private fun openRecorderFrom(startIndex: Int): Pair<AudioRecord, Int>? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            throw IllegalStateException("AudioRecord getMinBufferSize=$minBuf")
        }
        val bufferSize = maxOf(minBuf * 2, SAMPLE_RATE / 5 * 2)
        var lastError: Exception? = null
        for (i in startIndex until AUDIO_SOURCES.size) {
            val source = AUDIO_SOURCES[i]
            try {
                val rec = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    rec.release()
                    Timber.w("AudioRecord source=%d not initialized", source)
                    continue
                }
                try {
                    rec.startRecording()
                } catch (e: Exception) {
                    rec.release()
                    throw e
                }
                Timber.i("AudioRecord opened source=%d", source)
                audioSourceIndex = i
                return rec to i
            } catch (e: Exception) {
                lastError = e
                Timber.w(e, "AudioRecord source=%d failed", source)
            }
        }
        if (lastError != null) throw lastError
        return null
    }

    private fun swapRecorderIfPossible(
        current: AudioRecord,
        nextIndex: Int,
    ): Pair<AudioRecord, Int>? {
        if (nextIndex >= AUDIO_SOURCES.size) return null
        val opened = try {
            openRecorderFrom(nextIndex)
        } catch (e: Exception) {
            Timber.w(e, "AudioRecord fallback from index=%d failed", nextIndex)
            null
        } ?: return null
        releaseRecorder(current)
        recorder = opened.first
        audioSourceIndex = opened.second
        return opened
    }

    private fun releaseRecorder(rec: AudioRecord) {
        runCatching { rec.stop() }
        runCatching { rec.release() }
    }

    companion object {
        const val ERR_NO_AUDIO = "no audio"
        const val ERR_NO_SPEECH = "no speech"
        const val ERR_RECOGNIZE = "recognize failed"

        private const val SAMPLE_RATE = 16000
        private const val MIN_SEGMENT_SAMPLES = 400 // 25ms
        private const val PRE_ROLL_SAMPLES = 8000 // 0.5s
        private const val MAX_SESSION_MS = 120_000L
        private const val RECOGNIZER_IDLE_RELEASE_MS = 60_000L
        /** 首源近静音多久后换下一路（Elite 上 VOICE_RECOGNITION 常空）。 */
        private const val SILENCE_FALLBACK_MS = 1000L

        /**
         * 录音源优先级：MIC 优先。Titan Elite 等 OEM 上 VOICE_RECOGNITION 常能 init
         * 却只吐近零采样，若排第一会白白丢掉按住说话的前一秒。失败/静音再回退其它源。
         */
        private val AUDIO_SOURCES = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.DEFAULT,
        )
    }
}

/**
 * 近静音判定（与 AudioRecord 解耦，方便单测）。
 * peakAbs 是 float PCM 绝对值峰值；低于此阈值视为「麦克风没进声」。
 */
object VoiceCaptureSupport {
    const val SILENCE_PEAK = 0.01f

    fun isNearSilence(peakAbs: Float): Boolean = peakAbs < SILENCE_PEAK
}
