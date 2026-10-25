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
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * 薄荷输入法 / oh-my-rime（[Mintimate/oh-my-rime](https://github.com/Mintimate/oh-my-rime)）按需下载。
 *
 * 先缓存到 `data/rime-packs/mint/`；用户点选后再替换进 `data/rime/`。
 * 与雾凇 / 白霜缓存可并存。
 *
 * 下载优先 TapFeet 自建 `rime-packs` 镜像，失败再回落 CNB / GitHub（见 [RimePackUrls]）。
 */
object RimeMintManager {

    const val RELEASE_TAG = "2025.10.19-cnb"

    private const val ZIP_NAME = "oh-my-rime.zip"
    private const val CNB_ZIP =
        "https://cnb.cool/Mintimate/rime/oh-my-rime/-/releases/download/latest/oh-my-rime.zip"
    private const val GITHUB_COMMIT = "5fca6ae067fd666b5ec4f119c5147efd629cc76d"
    private const val GITHUB_ARCHIVE =
        "https://github.com/Mintimate/oh-my-rime/archive/$GITHUB_COMMIT.zip"

    private const val APPROX_MB = 23
    private const val PACK_ID = RimePackStore.ID_MINT

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
    private fun packDir(): File = RimePackStore.packDir(PACK_ID)
    private fun schemaFile(): File = File(packDir(), "rime_mint.schema.yaml")
    private fun dictFile(): File = File(packDir(), "rime_mint.dict.yaml")

    fun approxSizeMb(): Int = APPROX_MB

    fun isReady(): Boolean =
        RimePackStore.isPackCached(PACK_ID, RELEASE_TAG) &&
            schemaFile().isFile &&
            dictFile().isFile

    fun isDownloadInProgress(): Boolean = downloadJob?.isActive == true

    @Synchronized
    fun refreshState() {
        if (downloadJob?.isActive == true) return
        // 保留 Error，避免 state 被读取时冲掉失败提示，导致用户无法点同一项重试
        if (mutableState.value is State.Error) return
        mutableState.value = if (isReady()) State.Ready else State.NotDownloaded
    }

    @Synchronized
    fun clearInstallMarker() {
        val active = RimePackStore.readActive()
        if (active?.first == PACK_ID) {
            RimePackStore.activeMarkerFile().delete()
        }
        refreshState()
    }

    @Synchronized
    fun ensureDownloaded(onSuccess: (() -> Unit)? = null, onFailure: ((String) -> Unit)? = null) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            try {
                if (!isReady()) {
                    mutableState.value = State.Downloading(0, APPROX_MB * 1024L * 1024L)
                    downloadAndExtract()
                    if (!isReady()) throw IOException("薄荷文件校验失败")
                }
                RimePackStore.activate(PACK_ID, RELEASE_TAG)
                if (!isActive) return@launch
                mutableState.value = State.Ready
                onSuccess?.let { mainHandler.post(it) }
            } catch (e: Exception) {
                if (!isActive) return@launch
                Timber.e(e, "oh-my-rime download/activate failed")
                val message = e.message ?: "download failed"
                RimeDeployState.invalidate()
                mutableState.value = State.Error(message)
                onFailure?.let { cb -> mainHandler.post { cb(message) } }
            }
        }
    }

    @Synchronized
    fun delete() {
        downloadJob?.cancel()
        downloadJob = null
        RimePackStore.deletePackCache(PACK_ID)
        if (RimePackStore.readActive()?.first == PACK_ID) {
            RimePackStore.activeMarkerFile().delete()
        }
        mutableState.value = State.NotDownloaded
    }

    private fun downloadAndExtract() {
        val dir = packDir()
        dir.mkdirs()
        val part = File(dir, "$ZIP_NAME.part")
        part.delete()
        var last: Exception? = null
        for (url in RimePackUrls.candidates(RimePackUrls.ASSET_MINT, CNB_ZIP, GITHUB_ARCHIVE)) {
            try {
                downloadFile(url, part)
                extractZip(part, dir)
                RimePackStore.markPackCached(PACK_ID, RELEASE_TAG)
                part.delete()
                return
            } catch (e: Exception) {
                Timber.w(e, "oh-my-rime download failed: $url")
                last = e
                part.delete()
            }
        }
        throw last ?: IOException("oh-my-rime download failed")
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
                val raw = entry.name ?: continue
                val name = RimeMintZipPaths.normalizeEntryName(raw)
                if (name == null || RimeMintZipPaths.shouldSkipEntry(name)) {
                    zis.closeEntry()
                    continue
                }
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
        if (!schemaFile().isFile) throw IOException("missing rime_mint.schema.yaml")
        if (!dictFile().isFile) throw IOException("missing rime_mint.dict.yaml")
    }
}
