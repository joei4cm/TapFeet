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
 * 雾凇拼音（[iDvel/rime-ice](https://github.com/iDvel/rime-ice)）按需下载。
 *
 * 官方 `full_compiled.zip`（约 47MB）先落到 `data/rime-packs/ice/`；
 * 用户点选雾凇后再替换进 `data/rime/`。与白霜 / 薄荷缓存可并存。
 *
 * 下载优先 TapFeet 自建 `rime-packs` 镜像，失败再回落上游（见 [RimePackUrls]）。
 */
object RimeIceManager {

    const val RELEASE_TAG = "2026.06.30"

    private const val ZIP_NAME = "full_compiled.zip"
    private const val UPSTREAM_ZIP =
        "https://github.com/iDvel/rime-ice/releases/download/$RELEASE_TAG/$ZIP_NAME"

    private const val TABLE_MIN_BYTES = 50L * 1024L * 1024L
    private const val APPROX_MB = 47
    private const val PACK_ID = RimePackStore.ID_ICE

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
    private fun tableFile(): File = File(packDir(), "build/rime_ice.table.bin")
    private fun schemaFile(): File = File(packDir(), "rime_ice.schema.yaml")

    fun approxSizeMb(): Int = APPROX_MB

    /** 缓存已就绪（未必已替换进 data/rime）。 */
    fun isReady(): Boolean {
        migrateLegacyIfNeeded()
        return RimePackStore.isPackCached(PACK_ID, RELEASE_TAG) &&
            schemaFile().isFile &&
            tableFile().length() >= TABLE_MIN_BYTES
    }

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
        // 兼容旧互斥 API：不再清缓存，仅清 active 标记（若当前是本包）
        val active = RimePackStore.readActive()
        if (active?.first == PACK_ID) {
            RimePackStore.activeMarkerFile().delete()
        }
        refreshState()
    }

    /**
     * 确保雾凇已缓存，并**替换**进 `data/rime/`（幂等）。
     */
    @Synchronized
    fun ensureDownloaded(onSuccess: (() -> Unit)? = null, onFailure: ((String) -> Unit)? = null) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            try {
                if (!isReady()) {
                    mutableState.value = State.Downloading(0, APPROX_MB * 1024L * 1024L)
                    downloadAndExtract()
                    if (!isReady()) throw IOException("雾凇文件校验失败")
                }
                RimePackStore.activate(PACK_ID, RELEASE_TAG)
                if (!isActive) return@launch
                mutableState.value = State.Ready
                onSuccess?.let { mainHandler.post(it) }
            } catch (e: Exception) {
                if (!isActive) return@launch
                Timber.e(e, "rime-ice download/activate failed")
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
        RimePackStore.deletePackCache(PACK_ID)
        if (RimePackStore.readActive()?.first == PACK_ID) {
            RimePackStore.activeMarkerFile().delete()
        }
        mutableState.value = State.NotDownloaded
    }

    /** 旧版直接装在 data/rime/ 的雾凇：迁入 packs/ice。 */
    private fun migrateLegacyIfNeeded() {
        if (RimePackStore.isPackCached(PACK_ID, RELEASE_TAG)) return
        val legacyMarker = File(rimeUserDir(), ".tapfeet_rime_ice")
        val legacyTable = File(rimeUserDir(), "build/rime_ice.table.bin")
        val legacySchema = File(rimeUserDir(), "rime_ice.schema.yaml")
        if (!legacyMarker.isFile || legacyMarker.readText().trim() != RELEASE_TAG) return
        if (!legacySchema.isFile || legacyTable.length() < TABLE_MIN_BYTES) return
        try {
            val dest = packDir()
            dest.mkdirs()
            rimeUserDir().listFiles()?.forEach { child ->
                if (child.name.startsWith(".tapfeet_")) return@forEach
                val out = File(dest, child.name)
                if (child.isDirectory) child.copyRecursively(out, overwrite = true)
                else child.copyTo(out, overwrite = true)
            }
            RimePackStore.markPackCached(PACK_ID, RELEASE_TAG)
            RimePackStore.activeMarkerFile().writeText("$PACK_ID\n$RELEASE_TAG\n")
            legacyMarker.delete()
            Timber.i("migrated legacy rime-ice into pack cache")
        } catch (e: Exception) {
            Timber.w(e, "legacy rime-ice migrate failed")
        }
    }

    private fun downloadAndExtract() {
        val dir = packDir()
        dir.mkdirs()
        val part = File(dir, "$ZIP_NAME.part")
        part.delete()
        var last: Exception? = null
        for (url in RimePackUrls.candidates(RimePackUrls.ASSET_ICE, UPSTREAM_ZIP)) {
            try {
                downloadFile(url, part)
                extractZip(part, dir)
                RimePackStore.markPackCached(PACK_ID, RELEASE_TAG)
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
        if (tableFile().length() < TABLE_MIN_BYTES) {
            throw IOException("missing precompiled rime_ice.table.bin")
        }
        if (!schemaFile().isFile) {
            val built = File(packDir(), "build/rime_ice.schema.yaml")
            if (built.isFile) built.copyTo(schemaFile(), overwrite = true)
            else throw IOException("missing rime_ice.schema.yaml")
        }
    }
}
