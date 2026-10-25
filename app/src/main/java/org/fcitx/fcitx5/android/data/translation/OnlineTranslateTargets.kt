/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.translation

import org.fcitx.fcitx5.android.data.prefs.AppPrefs

/**
 * 联网翻译目标语种偏好：逗号分隔的 [OnlineTranslator.Lang] code（如 `en,ja,ko`）。
 * 纯解析逻辑可供单测；读设置走 [selected]。
 */
object OnlineTranslateTargets {

    const val PREF_KEY = "online_translate_targets"
    const val DEFAULT_RAW = "en,ja"
    const val MAX_SELECTED = 6

    /** 设置页多选里可选的全部语种（不含「自动检测源语言」）。 */
    val allSelectable: List<OnlineTranslator.Lang> = OnlineTranslator.Lang.entries

    fun parse(raw: String): List<OnlineTranslator.Lang> {
        val seen = LinkedHashSet<OnlineTranslator.Lang>()
        for (part in raw.split(',', ';', '，', '、')) {
            val lang = OnlineTranslator.Lang.fromCode(part) ?: continue
            seen += lang
            if (seen.size >= MAX_SELECTED) break
        }
        return if (seen.isEmpty()) {
            parse(DEFAULT_RAW)
        } else {
            seen.toList()
        }
    }

    fun encode(langs: Collection<OnlineTranslator.Lang>): String =
        langs.distinct().take(MAX_SELECTED).joinToString(",") { it.code }

    fun selected(): List<OnlineTranslator.Lang> =
        parse(AppPrefs.getInstance().translation.onlineTranslateTargets.getValue())

    fun summaryCodes(raw: String): String =
        parse(raw).joinToString(" · ") { it.label }
}
