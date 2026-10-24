/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.core

import org.fcitx.fcitx5.android.data.rime.RimeIceManager
import org.fcitx.fcitx5.android.data.rime.RimeSchemaSwitcher
import org.fcitx.fcitx5.android.input.keyboard.PinyinEngineKind
import timber.log.Timber

/**
 * Enable and activate a bundled Chinese IM. English (`keyboard-us`) and any other IMs stay put.
 * Selecting an engine **adds** it; it does not strip engines the user already enabled.
 *
 * 雾凇与朙月共用 fcitx `rime` 输入法，通过 [RimeSchemaSwitcher] 切换 schema。
 */
object ChinesePinyinEngine {
    const val PINYIN = "pinyin"
    const val SHUANGPIN = "shuangpin"
    const val RIME = "rime"
    const val WUBI = "wbx"

    val bundledNames: Set<String> = PinyinEngineKind.entries.map { it.uniqueName }.toSet()

    fun applyEnabled(enabled: List<String>, uniqueName: String): List<String> {
        if (enabled.contains(uniqueName)) return enabled
        val lastChinese = enabled.indexOfLast { it in bundledNames }
        if (lastChinese < 0) return listOf(uniqueName) + enabled
        return enabled.toMutableList().also { it.add(lastChinese + 1, uniqueName) }
    }

    fun shouldActivate(current: String, uniqueName: String, force: Boolean = false): String? {
        if (current == uniqueName) return null
        if (force || current in bundledNames) return uniqueName
        return null
    }
}

suspend fun FcitxAPI.syncChinesePinyinEngine(
    kind: PinyinEngineKind,
    forceActivate: Boolean = false,
) {
    val uniqueName = kind.uniqueName
    if (availableIme().none { it.uniqueName == uniqueName }) {
        Timber.w("$uniqueName input method is not available")
        return
    }

    val schemaId = kind.rimeSchemaId
    if (schemaId != null) {
        if (kind.needsDownload && !RimeIceManager.isReady()) {
            Timber.i("rime-ice not ready yet; skip schema switch")
            return
        }
        RimeSchemaSwitcher.apply(schemaId)
        if (!RimeSchemaSwitcher.deploy(this)) {
            Timber.w("rime deploy action not found; schema file written, will apply on next session")
        }
    }

    val enabled = enabledIme().map { it.uniqueName }
    val next = ChinesePinyinEngine.applyEnabled(enabled, uniqueName)
    if (next != enabled) {
        setEnabledIme(next.toTypedArray())
    }
    // 同一 uniqueName（rime）在朙月↔雾凇之间切换时也要 force 再激活一次，让引擎重读 schema
    val activate = ChinesePinyinEngine.shouldActivate(
        currentIme().uniqueName,
        uniqueName,
        forceActivate || schemaId != null,
    )
    if (activate != null) {
        activateIme(activate)
    } else if (forceActivate && schemaId != null && currentIme().uniqueName == uniqueName) {
        // 已在 rime 上：再 activate 一次触发重载
        activateIme(uniqueName)
    }
}
