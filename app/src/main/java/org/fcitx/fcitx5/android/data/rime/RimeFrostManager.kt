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
import org.fcitx.fcitx5.android.input.keyboard.PinyinEngineKind
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * 白霜拼音（[gaboolic/rime-frost](https://github.com/gaboolic/rime-frost)）按需下载。
 *
 * 上游 Release 只提供源配置包（无预编译 `.table.bin`），解压到 `{externalFiles}/data/rime/`
 * 后由 fcitx5-rime Deploy 首次编译。细胞词库体量过大，安装时裁成手机可用的核心表
 *（8105 / base / ext），保留白霜词频，避免 Elite 上部署数十分钟或 OOM。
 * 与雾凇互斥：装白霜会清雾凇标记，反之亦然。
 */
object RimeFrostManager {

    const val RELEASE_TAG = "1.0.4"

    private const val ZIP_NAME = "rime-frost-schemas.zip"
    private const val GITHUB_ZIP =
        "https://github.com/gaboolic/rime-frost/releases/download/$RELEASE_TAG/$ZIP_NAME"

    private const val APPROX_MB = 42

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

    fun rimeUserDir(): File = RimePaths.userDir()

    private fun markerFile(): File = File(rimeUserDir(), ".tapfeet_rime_frost")
    private fun schemaFile(): File = File(rimeUserDir(), "rime_frost.schema.yaml")
    private fun dictFile(): File = File(rimeUserDir(), "rime_frost.dict.yaml")

    fun approxSizeMb(): Int = APPROX_MB

    fun isReady(): Boolean {
        val marker = markerFile()
        if (!marker.isFile || marker.readText().trim() != RELEASE_TAG) return false
        return schemaFile().isFile && dictFile().isFile
    }

    fun isDownloadInProgress(): Boolean = downloadJob?.isActive == true

    @Synchronized
    fun refreshState() {
        if (downloadJob?.isActive == true) return
        mutableState.value = if (isReady()) State.Ready else State.NotDownloaded
    }

    @Synchronized
    fun clearInstallMarker() {
        markerFile().delete()
        refreshState()
    }

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
                if (!isReady()) throw IOException("白霜文件校验失败")
                if (!isActive) return@launch
                mutableState.value = State.Ready
                onSuccess?.let { mainHandler.post(it) }
            } catch (e: Exception) {
                if (!isActive) return@launch
                Timber.e(e, "rime-frost download failed")
                val message = e.message ?: "download failed"
                mutableState.value = State.Error(message)
                onFailure?.let { cb -> mainHandler.post { cb(message) } }
            }
        }
    }

    @Synchronized
    fun delete() {
        downloadJob?.cancel()
        downloadJob = null
        val dir = rimeUserDir()
        markerFile().delete()
        listOf(
            "rime_frost.schema.yaml",
            "rime_frost.dict.yaml",
            "rime_frost_aux.schema.yaml",
            "rime_frost_aux.dict.yaml",
            "build/rime_frost.table.bin",
            "build/rime_frost.prism.bin",
            "build/rime_frost.reverse.bin",
            "build/rime_frost.schema.yaml",
        ).forEach { File(dir, it).delete() }
        refreshState()
    }

    private fun downloadAndExtract() {
        RimePackExclusivity.clearOthers(PinyinEngineKind.RimeFrost)
        val dir = rimeUserDir()
        val part = File(dir, "$ZIP_NAME.part")
        part.delete()
        var last: Exception? = null
        for (url in RimePaths.githubMirrors(GITHUB_ZIP)) {
            try {
                downloadFile(url, part)
                extractZip(part, dir)
                slimDictFile(dictFile())
                markerFile().writeText(RELEASE_TAG)
                part.delete()
                return
            } catch (e: Exception) {
                Timber.w(e, "rime-frost download failed: $url")
                last = e
                part.delete()
            }
        }
        throw last ?: IOException("rime-frost download failed")
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
        dest.inputStream().use { ins ->
            val head = ByteArray(16)
            val n = ins.read(head)
            val s = head.decodeToString(0, n.coerceAtLeast(0)).trimStart()
            if (s.startsWith("<") || s.startsWith("version https://git-lfs")) {
                throw IOException("not a zip")
            }
        }
    }

    private fun extractZip(zip: File, destDir: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name ?: continue
                val outFile = File(destDir, name)
                val destPath = destDir.canonicalPath + File.separator
                if (!outFile.canonicalPath.startsWith(destPath)) {
                    throw IOException("zip slip: $name")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    if (outFile.name == "default.custom.yaml" && outFile.exists()) {
                        zis.closeEntry()
                        continue
                    }
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
            }
        }
        if (!schemaFile().isFile) throw IOException("missing rime_frost.schema.yaml")
        if (!dictFile().isFile) throw IOException("missing rime_frost.dict.yaml")
    }

    /** 去掉细胞词库 / 大字表，保留白霜核心字词频。 */
    private fun slimDictFile(dict: File) {
        if (!dict.isFile) return
        val original = dict.readText(Charsets.UTF_8)
        val slimmed = RimeFrostDictSlim.apply(original)
        if (!RimeFrostDictSlim.changed(original, slimmed)) {
            Timber.w("rime-frost dict slim: import_tables block not found, keeping upstream")
            return
        }
        dict.writeText(slimmed, Charsets.UTF_8)
    }
}
