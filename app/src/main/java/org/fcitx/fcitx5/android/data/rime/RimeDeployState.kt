/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

import java.io.File

/**
 * 记录最近一次成功请求的 Rime Deploy 指纹，避免每次启动 / 切回同一方案都跑全量维护。
 * 指纹 = schemaId + 当前 active pack（或 bundled）。
 */
object RimeDeployState {

    private const val MARKER = ".tapfeet_deployed"

    @Volatile
    private var memory: String? = null

    fun fingerprint(schemaId: String): String {
        val active = RimePackStore.readActive()
        return if (active != null) {
            "$schemaId|${active.first}|${active.second}"
        } else {
            "$schemaId|bundled"
        }
    }

    fun isCurrent(schemaId: String): Boolean {
        val fp = fingerprint(schemaId)
        if (memory == fp) return true
        val f = markerFile()
        if (!f.isFile) return false
        val disk = f.readText().trim()
        if (disk == fp) {
            memory = fp
            return true
        }
        return false
    }

    fun mark(schemaId: String) {
        val fp = fingerprint(schemaId)
        memory = fp
        RimePaths.userDir().mkdirs()
        markerFile().writeText(fp)
    }

    /** 包文件被替换或清空后调用，强制下次 sync 重新 Deploy。 */
    fun invalidate() {
        memory = null
        markerFile().delete()
    }

    private fun markerFile(): File = File(RimePaths.userDir(), MARKER)
}
