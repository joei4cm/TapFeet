/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.fcitx.fcitx5.android.input.keyboard.PinyinEngineKind

/**
 * 引擎「加载中」态（与下载态分开）：包已就绪、正在 activate / deploy / 切 IM。
 */
object RimeApplyState {
    private val mutable = MutableStateFlow<PinyinEngineKind?>(null)
    val applying: StateFlow<PinyinEngineKind?> = mutable

    fun begin(kind: PinyinEngineKind) {
        mutable.value = kind
    }

    fun end(kind: PinyinEngineKind) {
        if (mutable.value == kind) mutable.value = null
    }

    fun isApplying(kind: PinyinEngineKind): Boolean = mutable.value == kind
}
