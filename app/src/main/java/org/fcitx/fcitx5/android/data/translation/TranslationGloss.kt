/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.translation

import org.fcitx.fcitx5.android.utils.appContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * 本地释义表（assets `translation/zh_ja_en_gloss.tsv`）。
 *
 * 每行：`原文\t英文[\t日文]`
 * - 中文词：forward 查英/日
 * - 日文词：同样走 forward（原文可以是假名/汉字）
 * - 英文：reverse 反查原文
 */
object TranslationGloss {

    data class Entry(val en: String?, val ja: String?)

    private const val ASSET = "translation/zh_ja_en_gloss.tsv"

    private data class Tables(
        val forward: Map<String, Entry>,
        val reverse: Map<String, String>,
    )

    private val tables = AtomicReference<Tables?>(null)

    @Synchronized
    fun ensureLoaded() {
        if (tables.get() != null) return
        tables.set(load())
    }

    fun entryOf(text: String): Entry? {
        val t = tables.get() ?: return null
        if (text.isBlank()) return null
        t.forward[text]?.let { return it }
        if (text.length > 1) {
            for (len in text.length - 1 downTo 1) {
                t.forward[text.substring(0, len)]?.let { return it }
            }
        }
        return null
    }

    fun glossOf(text: String): String? = entryOf(text)?.en

    fun sourceOfEnglish(english: String): String? {
        val t = tables.get() ?: return null
        val key = english.trim().lowercase()
        if (key.isEmpty()) return null
        return t.reverse[key]
    }

    fun isLoaded(): Boolean = tables.get() != null

    internal fun replaceForTest(forward: Map<String, Entry>, reverse: Map<String, String>) {
        tables.set(Tables(forward, reverse))
    }

    internal fun clearForTest() {
        tables.set(null)
    }

    private fun load(): Tables {
        val forward = HashMap<String, Entry>(1024)
        val reverse = HashMap<String, String>(1024)
        try {
            appContext.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val cols = line.split('\t')
                    if (cols.size < 2) return@forEach
                    val src = cols[0].trim()
                    val en = cols[1].trim().ifEmpty { null }
                    val ja = cols.getOrNull(2)?.trim()?.ifEmpty { null }
                    if (src.isEmpty() || (en == null && ja == null)) return@forEach
                    forward.putIfAbsent(src, Entry(en, ja))
                    val firstEn = en?.split(';', ',')?.firstOrNull()?.trim()?.lowercase()
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
