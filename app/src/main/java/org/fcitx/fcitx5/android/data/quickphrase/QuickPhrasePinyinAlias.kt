/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.quickphrase

import org.fcitx.fcitx5.android.data.pinyin.PinyinLookup
import timber.log.Timber
import java.io.File

/**
 * Extra QuickPhrase.mb keys so typing 拼音 (full or initials) finds a Chinese phrase.
 *
 * fcitx QuickPhrase steals keys before the pinyin engine, so the buffer is always Latin.
 * Matching is keyword-prefix only; this file adds `zaoshanghao` / `zsh` → `早上好` next to a
 * user keyword like `zq`. Regenerated from enabled phrase files; not shown in the editor.
 */
object QuickPhrasePinyinAlias {

    const val FILE_NAME = "zz-pinyin-alias.mb"

    fun aliasEntries(
        entries: List<QuickPhraseEntry>,
        pinyinOf: (String) -> String?,
        initialsOf: (String) -> String?,
    ): List<QuickPhraseEntry> {
        val existing = entries.map { it.keyword to it.phrase }.toHashSet()
        val extra = LinkedHashSet<QuickPhraseEntry>()
        for (entry in entries) {
            val keys = linkedSetOf<String>()
            pinyinOf(entry.phrase)?.let { keys += it }
            initialsOf(entry.phrase)?.let { keys += it }
            pinyinOf(entry.keyword)?.let { keys += it }
            initialsOf(entry.keyword)?.let { keys += it }
            for (key in keys) {
                if (key.isEmpty() || key == entry.keyword) continue
                if (!existing.add(key to entry.phrase)) continue
                extra += QuickPhraseEntry(key, entry.phrase)
            }
        }
        return extra.toList()
    }

    fun syncTo(dir: File) {
        dir.mkdirs()
        val dest = File(dir, FILE_NAME)
        val entries = QuickPhraseManager.listQuickPhrase()
            .filter { it.isEnabled }
            .flatMap { runCatching { it.loadData().toList() }.getOrDefault(emptyList()) }
        val extra = aliasEntries(entries, PinyinLookup::pinyinOf, PinyinLookup::initialsOf)
        val body = extra.joinToString("\n") { it.serialize() }
        if (body.isEmpty()) {
            if (dest.exists()) dest.delete()
            return
        }
        dest.writeText(body)
        Timber.d("QuickPhrase pinyin alias: ${extra.size} keys")
    }
}
