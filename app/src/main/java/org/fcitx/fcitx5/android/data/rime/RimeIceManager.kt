/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.fcitx.fcitx5.android.utils.appContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * 雾凇拼音（[iDvel/rime-ice](https://github.com/iDvel/rime-ice)）按需下载。
 *
 * 使用官方 Release 的 `full_compiled.zip`（含源配置 + 预编译词库，约 47MB），
 * 解压到 `{externalFiles}/data/rime/`，与手册中的手动导入路径一致。
 * 不打进 APK，避免体积暴涨。
 */
object RimeIceManager {

    /** 锁定的上游 Release 标签；升级雾凇时改这里并测一遍。 */
    const val RELEASE_TAG = "2026.06.30"

    private const val ZIP_NAME = "full_compiled.zip"
    private const val GITHUB_ZIP =
        "https://github.com/iDvel/rime-ice/releases/download/$RELEASE_TAG/$ZIP_NAME"

    /** 预编译主表下限，用于拒收残包/HTML。 */
    private const val TABLE_MIN_BYTES = 50L * 1024L * 1024L
    private const val APPROX_MB = 47

    sealed class State {
        data object NotDownloaded : State()
        data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : State()
        data object Ready : State()
        data class Error(val message: String) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow<State>(State.NotDownloaded)
    val state: StateFlow<State> get() {
        refreshState()
        return mutableState
    }

    private var downloadJob: Job? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun rimeUserDir(): File =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "data/rime").also {
            it.mkdirs()
        }

    private fun markerFile(): File = File(rimeUserDir(), ".tapfeet_rime_ice")
    private fun tableFile(): File = File(rimeUserDir(), "build/rime_ice.table.bin")
    private fun schemaFile(): File = File(rimeUserDir(), "rime_ice.schema.yaml")

    fun approxSizeMb(): Int = APPROX_MB

    fun isReady(): Boolean {
        val marker = markerFile()
        if (!marker.isFile || marker.readText().trim() != RELEASE_TAG) return false
        if (!schemaFile().isFile) return false
        return tableFile().length() >= TABLE_MIN_BYTES
    }

    fun isDownloadInProgress(): Boolean = downloadJob?.isActive == true

    @Synchronized
    fun refreshState() {
        if (downloadJob?.isActive == true) return
        mutableState.value = if (isReady()) State.Ready else State.NotDownloaded
    }

    /**
     * 确保雾凇包就绪；缺失时开始下载（幂等）。
     */
    @Synchronized
    fun ensureDownloaded(onSuccess: (() -> Unit)? = null, onFailure: ((String) -> Unit)? = null) {
        if (isReady()) {
            mutableState.value = State.Ready
            onSuccess?.let { mainHandler.post(it) }
            return
        }
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            try {
                mutableState.value = State.Downloading(0, APPROX_MB * 1024L * 1024L)
                downloadAndExtract()
                if (!isReady()) throw IOException("雾凇文件校验失败")
                if (!isActive) return@launch
                mutableState.value = State.Ready
                onSuccess?.let { mainHandler.post(it) }
            } catch (e: Exception) {
                if (!isActive) return@launch
                Timber.e(e, "rime-ice download failed")
                val message = e.message ?: "download failed"
                mutableState.value = State.Error(message)
                onFailure?.let { cb -> mainHandler.post { cb(message) } }
            }
        }
    }

    /** 删除雾凇安装痕迹（不碰用户自定义的其它 rime 文件，尽量只清冰词相关）。 */
    @Synchronized
    fun delete() {
        downloadJob?.cancel()
        downloadJob = null
        val dir = rimeUserDir()
        markerFile().delete()
        listOf(
            "rime_ice.schema.yaml",
            "rime_ice.dict.yaml",
            "build/rime_ice.table.bin",
            "build/rime_ice.prism.bin",
            "build/rime_ice.reverse.bin",
            "build/rime_ice.schema.yaml",
        ).forEach { File(dir, it).delete() }
        File(dir, "cn_dicts").deleteRecursively()
        File(dir, "en_dicts").deleteRecursively()
        // lua / opencc / custom_phrase 可能被用户改过；有 marker 时整目录清过一次更干净，
        // 这里只在确认是我们装的包时删掉 ice 专用 lua 名（保留用户自建）。
        refreshState()
    }

    private fun downloadAndExtract() {
        val dir = rimeUserDir()
        val part = File(dir, "$ZIP_NAME.part")
        part.delete()
        var last: Exception? = null
        for (url in githubMirrors(GITHUB_ZIP)) {
            try {
                downloadFile(url, part)
                extractZip(part, dir)
                markerFile().writeText(RELEASE_TAG)
                part.delete()
                return
            } catch (e: Exception) {
                Timber.w(e, "rime-ice download failed: $url")
                last = e
                part.delete()
            }
        }
        throw last ?: IOException("rime-ice download failed")
    }

    private fun downloadFile(url: String, dest: File) {
        val request = Request.Builder().url(url).header("User-Agent", "TapFeet-IME").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: (APPROX_MB * 1024L * 1024L)
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastEmit = 0L
                body.byteStream().use { ins ->
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastEmit >= 512 * 1024) {
                            lastEmit = done
                            mutableState.value = State.Downloading(done, total)
                        }
                    }
                }
            }
        }
        if (dest.length() < 1024 * 1024) throw IOException("zip too small")
        // 拒收 HTML / LFS 指针
        dest.inputStream().use { ins ->
            val head = ByteArray(16)
            val n = ins.read(head)
            val s = head.decodeToString(0, n.coerceAtLeast(0)).trimStart()
            if (s.startsWith("<") || s.startsWith("version https://git-lfs")) {
                throw IOException("not a zip: $urlProxyHint")
            }
        }
    }

    private const val urlProxyHint = "bad download payload"

    private fun extractZip(zip: File, destDir: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name ?: continue
                // Zip slip
                val outFile = File(destDir, name)
                val destPath = destDir.canonicalPath + File.separator
                if (!outFile.canonicalPath.startsWith(destPath)) {
                    throw IOException("zip slip: $name")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    // 不覆盖我们写的 schema 切换补丁
                    if (outFile.name == "default.custom.yaml" && outFile.exists()) {
                        zis.closeEntry()
                        continue
                    }
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
            }
        }
        if (tableFile().length() < TABLE_MIN_BYTES) {
            throw IOException("missing precompiled rime_ice.table.bin")
        }
        if (!schemaFile().isFile) {
            // 少数包只把 schema 放在 build/；补一份到根目录供 Rime 识别
            val built = File(rimeUserDir(), "build/rime_ice.schema.yaml")
            if (built.isFile) built.copyTo(schemaFile(), overwrite = true)
            else throw IOException("missing rime_ice.schema.yaml")
        }
    }

    private fun githubMirrors(url: String): List<String> = listOf(
        url,
        "https://ghfast.top/$url",
        "https://gh-proxy.com/$url",
    )
}
