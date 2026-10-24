/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

import org.fcitx.fcitx5.android.input.keyboard.PinyinEngineKind

/** 需联网下载的中州韵方案包（雾凇 / 白霜）。 */
object RimeDownloadablePack {

    fun isReady(kind: PinyinEngineKind): Boolean = when (kind) {
        PinyinEngineKind.RimeIce -> RimeIceManager.isReady()
        PinyinEngineKind.RimeFrost -> RimeFrostManager.isReady()
        else -> true
    }

    fun approxSizeMb(kind: PinyinEngineKind): Int = when (kind) {
        PinyinEngineKind.RimeIce -> RimeIceManager.approxSizeMb()
        PinyinEngineKind.RimeFrost -> RimeFrostManager.approxSizeMb()
        else -> 0
    }

    fun ensureDownloaded(
        kind: PinyinEngineKind,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
    ) {
        when (kind) {
            PinyinEngineKind.RimeIce ->
                RimeIceManager.ensureDownloaded(onSuccess, onFailure)
            PinyinEngineKind.RimeFrost ->
                RimeFrostManager.ensureDownloaded(onSuccess, onFailure)
            else -> onSuccess?.invoke()
        }
    }
}
