/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.quickphrase

import org.fcitx.fcitx5.android.data.pinyin.PinyinLookup

/**
 * In-app phrase search so Sym+G is not stuck in fcitx's Latin-only QuickPhrase buffer.
 * Matches keyword, phrase text, 全拼, and 声母. Chinese in the query matches the phrase itself.
 */
object QuickPhraseSearch {

    data class Hit(
        val keyword: String,
        val phrase: String,
        val pinyin: String,
        val initials: String,
    )

    fun load(
        sources: List<QuickPhrase> = QuickPhraseManager.listQuickPhrase().filter { it.isEnabled },
        pinyinOf: (String) -> String? = PinyinLookup::pinyinOf,
        initialsOf: (String) -> String? = PinyinLookup::initialsOf,
    ): List<Hit> {
        val seen = LinkedHashSet<String>()
        val hits = mutableListOf<Hit>()
        for (file in sources) {
            val entries = runCatching { file.loadData().toList() }.getOrDefault(emptyList())
            for (entry in entries) {
                val phrase = entry.phrase
                if (phrase.isBlank()) continue
                if (!seen.add("${entry.keyword}\u0000$phrase")) continue
                hits += Hit(
                    keyword = entry.keyword,
                    phrase = phrase,
                    pinyin = pinyinOf(phrase).orEmpty().ifEmpty { pinyinOf(entry.keyword).orEmpty() },
                    initials = initialsOf(phrase).orEmpty().ifEmpty {
                        initialsOf(entry.keyword).orEmpty()
                    },
                )
            }
        }
        return hits
    }

    fun filter(hits: List<Hit>, query: String): List<Hit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return hits
        val han = q.any { it.code in 0x4E00..0x9FFF }
        return hits.filter { hit ->
            if (han) return@filter hit.phrase.contains(query.trim())
            hit.keyword.lowercase().startsWith(q) ||
                hit.phrase.lowercase().contains(q) ||
                hit.pinyin.startsWith(q) ||
                hit.pinyin.contains(q) ||
                hit.initials.startsWith(q)
        }
    }
}
