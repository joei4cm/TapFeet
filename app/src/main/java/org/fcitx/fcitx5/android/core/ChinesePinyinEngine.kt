/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.core

import org.fcitx.fcitx5.android.data.rime.RimeApplyState
import org.fcitx.fcitx5.android.data.rime.RimeDeployState
import org.fcitx.fcitx5.android.data.rime.RimeDownloadablePack
import org.fcitx.fcitx5.android.data.rime.RimeSchemaSwitcher
import org.fcitx.fcitx5.android.input.keyboard.PinyinEngineKind
import timber.log.Timber

/**
 * Enable and activate a bundled Chinese IM. English (`keyboard-us`) and any other IMs stay put.
 * Selecting an engine **adds** it; it does not strip engines the user already enabled.
 *
 * 雾凇 / 白霜 / 薄荷与朙月共用 fcitx `rime` 输入法，通过 [RimeSchemaSwitcher] 切换 schema。
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

/**
 * @param forceActivate 用户点选时强制切到该引擎；启动恢复时勿抢当前 IM
 * @param forceDeploy 用户重试时强制 Deploy；否则仅在 schema/pack 指纹变化时 Deploy
 */
suspend fun FcitxAPI.syncChinesePinyinEngine(
    kind: PinyinEngineKind,
    forceActivate: Boolean = false,
    forceDeploy: Boolean = false,
) {
    val uniqueName = kind.uniqueName
    if (availableIme().none { it.uniqueName == uniqueName }) {
        Timber.w("$uniqueName input method is not available")
        return
    }

    val schemaId = kind.rimeSchemaId
    if (schemaId != null) {
        if (kind.needsDownload && !RimeDownloadablePack.isReady(kind)) {
            Timber.i("rime pack not ready yet kind=%s; skip schema switch", kind)
            return
        }
        RimeApplyState.begin(kind)
        try {
            RimeSchemaSwitcher.apply(schemaId)
        } catch (e: Exception) {
            RimeApplyState.end(kind)
            throw e
        }
    }

    try {
        val enabled = enabledIme().map { it.uniqueName }
        val next = ChinesePinyinEngine.applyEnabled(enabled, uniqueName)
        if (next != enabled) {
            setEnabledIme(next.toTypedArray())
        }
        val activate = ChinesePinyinEngine.shouldActivate(
            currentIme().uniqueName,
            uniqueName,
            forceActivate,
        )
        if (activate != null) {
            activateIme(activate)
        } else if (forceActivate && schemaId != null && currentIme().uniqueName == uniqueName) {
            activateIme(uniqueName)
        }

        if (schemaId != null) {
            val needDeploy = forceDeploy || !RimeDeployState.isCurrent(schemaId)
            if (needDeploy) {
                RimeSchemaSwitcher.deploy(this)
                RimeDeployState.mark(schemaId)
            } else {
                Timber.i("rime deploy skipped; fingerprint current for %s", schemaId)
            }
        }
    } finally {
        if (schemaId != null) RimeApplyState.end(kind)
    }
}
