/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * 雾凇 / 白霜 / 薄荷：各自缓存在 `data/rime-packs/<id>/`，
 * 用户点选对应引擎后再把该包**替换**进 `data/rime/`（fcitx5-rime 实际读取处）。
 * 三套可同时缓存；切换已下载的包只需本地拷贝，不必重下。
 */
object RimePackStore {

    const val ID_ICE = "ice"
    const val ID_FROST = "frost"
    const val ID_MINT = "mint"

    private const val ACTIVE_MARKER = ".tapfeet_active_pack"
    private const val PACK_MARKER = ".tapfeet_pack"

    fun packsRoot(): File =
        File(RimePaths.userDir().parentFile ?: RimePaths.userDir(), "rime-packs").also { it.mkdirs() }

    fun packDir(packId: String): File =
        File(packsRoot(), packId).also { it.mkdirs() }

    fun packMarker(packId: String): File = File(packDir(packId), PACK_MARKER)

    fun activeMarkerFile(): File = File(RimePaths.userDir(), ACTIVE_MARKER)

    fun isPackCached(packId: String, version: String): Boolean {
        val marker = packMarker(packId)
        return marker.isFile && marker.readText().trim() == version
    }

    fun readActive(): Pair<String, String>? {
        val f = activeMarkerFile()
        if (!f.isFile) return null
        val lines = f.readText().trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        return lines[0] to lines[1]
    }

    fun isActive(packId: String, version: String): Boolean {
        val active = readActive() ?: return false
        return active.first == packId && active.second == version
    }

    fun markPackCached(packId: String, version: String) {
        packMarker(packId).writeText(version)
    }

    /**
     * 把 [packId] 缓存目录整包替换进 `data/rime/`。
     * 保留用户可能自建的 `default.custom.yaml` 之外的策略：由 [RimeSchemaSwitcher] 随后重写。
     */
    @Synchronized
    fun activate(packId: String, version: String) {
        val src = packDir(packId)
        if (!isPackCached(packId, version)) {
            throw IOException("pack not cached: $packId@$version")
        }
        if (isActive(packId, version) && RimePaths.userDir().resolvePresence()) {
            Timber.i("rime pack already active: %s@%s", packId, version)
            return
        }
        val dest = RimePaths.userDir()
        clearActiveDir(dest)
        copyTree(src, dest)
        activeMarkerFile().writeText("$packId\n$version\n")
        Timber.i("rime pack activated: %s@%s -> %s", packId, version, dest.absolutePath)
    }

    /** 删除某套缓存（不影响当前 active，除非调方随后处理）。 */
    fun deletePackCache(packId: String) {
        packDir(packId).deleteRecursively()
    }

    private fun File.resolvePresence(): Boolean =
        listFiles()?.any { it.name != ACTIVE_MARKER && !it.name.startsWith(".") } == true

    private fun clearActiveDir(dir: File) {
        dir.mkdirs()
        dir.listFiles()?.forEach { child ->
            if (child.name == ACTIVE_MARKER) {
                child.delete()
                return@forEach
            }
            // 不删用户可能放的无关隐藏文件以外的内容：整包替换，清空非 marker
            child.deleteRecursively()
        }
    }

    private fun copyTree(src: File, dest: File) {
        if (!src.isDirectory) throw IOException("pack dir missing: ${src.absolutePath}")
        src.walkTopDown().forEach { file ->
            val rel = file.relativeTo(src).path
            if (rel.isEmpty()) return@forEach
            // 缓存标记不要拷进 active
            if (file.name == PACK_MARKER) return@forEach
            val out = File(dest, rel)
            if (file.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                file.copyTo(out, overwrite = true)
            }
        }
    }
}
