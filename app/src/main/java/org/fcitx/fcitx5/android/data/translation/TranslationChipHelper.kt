/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.translation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 管理翻译上屏芯片：本地释义立即出，联网整句防抖后补上。
 * [onUpdate] 在调用方 [scope] 所在调度器回调合并后的条目。
 */
class TranslationChipHelper(
    private val scope: CoroutineScope,
    private val onUpdate: (List<TranslationCommit.Item>) -> Unit,
) {
    private var job: Job? = null
    private var source: String = ""
    private var lastOnline: List<OnlineTranslator.Hit> = emptyList()

    fun setSource(text: String?) {
        val src = text?.trim().orEmpty()
        if (src == source) {
            publish(src, lastOnline)
            return
        }
        source = src
        lastOnline = emptyList()
        job?.cancel()
        if (src.isEmpty() || !TranslationCommit.wantsAny()) {
            onUpdate(emptyList())
            return
        }
        publish(src, emptyList())
        if (!TranslationCommit.isOnlineEnabled()) return
        val captured = src
        job = scope.launch {
            delay(450)
            if (source != captured) return@launch
            val hits = runCatching { OnlineTranslator.translateOthers(captured) }
                .getOrDefault(emptyList())
            if (source != captured) return@launch
            lastOnline = hits
            publish(captured, hits)
        }
    }

    fun clear() {
        job?.cancel()
        source = ""
        lastOnline = emptyList()
        onUpdate(emptyList())
    }

    private fun publish(src: String, online: List<OnlineTranslator.Hit>) {
        if (src.isEmpty() || !TranslationCommit.wantsAny()) {
            onUpdate(emptyList())
            return
        }
        val local = TranslationCommit.localItems(src)
        onUpdate(TranslationCommit.merge(local, TranslationCommit.onlineItems(online)))
    }
}
