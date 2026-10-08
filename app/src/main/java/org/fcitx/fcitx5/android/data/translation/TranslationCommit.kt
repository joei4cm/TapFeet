/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.translation

import org.fcitx.fcitx5.android.data.prefs.AppPrefs

/**
 * 翻译结果可上屏条目：本地释义芯片 + 联网整句结果。
 * 点选后由候选栏 chipStrip 走 commitText + reset，不经引擎 select。
 */
object TranslationCommit {

    data class Item(val label: String, val text: String)

    fun isLocalEnabled(): Boolean =
        AppPrefs.getInstance().candidateBar.showTranslationComment.getValue()

    fun isOnlineEnabled(): Boolean =
        AppPrefs.getInstance().candidateBar.onlineSentenceTranslate.getValue()

    fun wantsAny(): Boolean = isLocalEnabled() || isOnlineEnabled()

    /**
     * 本地词表：把首候选（或当前源文）的英/日/中译文做成可点芯片。
     * [enabled] 默认读设置；单测可直接传 true。
     */
    fun localItems(source: String, enabled: Boolean = isLocalEnabled()): List<Item> {
        if (!enabled) return emptyList()
        val src = source.trim()
        if (src.isEmpty()) return emptyList()
        TranslationGloss.ensureLoaded()
        val latin = src.isNotEmpty() && src.all { it.isLetter() && it.code < 128 }
        if (latin) {
            val zh = TranslationGloss.sourceOfEnglish(src) ?: return emptyList()
            return listOf(Item("${OnlineTranslator.Lang.Zh.label} $zh", zh))
        }
        val entry = TranslationGloss.entryOf(src) ?: return emptyList()
        val out = ArrayList<Item>(2)
        firstSense(entry.en)?.takeIf { !it.equals(src, ignoreCase = true) }?.let {
            out += Item("${OnlineTranslator.Lang.En.label} $it", it)
        }
        entry.ja?.trim()?.takeIf { it.isNotEmpty() && it != src }?.let {
            out += Item("${OnlineTranslator.Lang.Ja.label} $it", it)
        }
        return out
    }

    fun onlineItems(hits: List<OnlineTranslator.Hit>): List<Item> =
        hits.map { Item("${it.lang.label} ${it.text}", it.text) }

    /**
     * 合并本地 + 联网，按上屏文本去重（本地优先）。
     */
    fun merge(local: List<Item>, online: List<Item>): List<Item> {
        if (local.isEmpty()) return online
        if (online.isEmpty()) return local
        val seen = local.map { it.text.lowercase() }.toMutableSet()
        val out = local.toMutableList()
        for (item in online) {
            if (seen.add(item.text.lowercase())) out += item
        }
        return out
    }

    private fun firstSense(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.split(';', ',', '；', '，')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }
}
