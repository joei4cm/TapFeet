/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.translation

import org.fcitx.fcitx5.android.utils.appContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * 本地中/日 → 英释义表（assets `translation/zh_ja_en_gloss.tsv`）。
 * 格式每行：`词\t英文释义`；加载后同时建英文 → 原文的反向索引（取第一条）。
 *
 * 体积刻意保持很小（可随版本扩充）；不做联网翻译。
 */
object TranslationGloss {

    private const val ASSET = "translation/zh_ja_en_gloss.tsv"

    private data class Tables(
        val forward: Map<String, String>,
        val reverse: Map<String, String>,
    )

    private val tables = AtomicReference<Tables?>(null)

    @Synchronized
    fun ensureLoaded() {
        if (tables.get() != null) return
        tables.set(load())
    }

    fun glossOf(text: String): String? {
        val t = tables.get() ?: return null
        if (text.isBlank()) return null
        t.forward[text]?.let { return it }
        // 多字词：最长前缀命中（最多扫到全文长度，短词优先已在 map）
        if (text.length > 1) {
            for (len in text.length - 1 downTo 1) {
                t.forward[text.substring(0, len)]?.let { return it }
            }
        }
        return null
    }

    /** 英文单词 → 中/日原文（词表反向）。 */
    fun sourceOfEnglish(english: String): String? {
        val t = tables.get() ?: return null
        val key = english.trim().lowercase()
        if (key.isEmpty()) return null
        return t.reverse[key]
    }

    fun isLoaded(): Boolean = tables.get() != null

    /** 测试用：直接注入表。 */
    internal fun replaceForTest(forward: Map<String, String>, reverse: Map<String, String>) {
        tables.set(Tables(forward, reverse))
    }

    internal fun clearForTest() {
        tables.set(null)
    }

    private fun load(): Tables {
        val forward = HashMap<String, String>(1024)
        val reverse = HashMap<String, String>(1024)
        try {
            appContext.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@forEach
                    val src = line.substring(0, tab).trim()
                    val gloss = line.substring(tab + 1).trim()
                    if (src.isEmpty() || gloss.isEmpty()) return@forEach
                    forward.putIfAbsent(src, gloss)
                    // 反向：取释义里第一个英文词条
                    val firstEn = gloss.split(';', ',').firstOrNull()?.trim()?.lowercase()
                    if (!firstEn.isNullOrEmpty() && firstEn[0] in 'a'..'z') {
                        reverse.putIfAbsent(firstEn, src)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "failed to load translation gloss")
        }
        Timber.i("translation gloss loaded forward=%d reverse=%d", forward.size, reverse.size)
        return Tables(forward, reverse)
    }
}
