/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.candidates

import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.pinyin.PinyinLookup
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.translation.TranslationGloss

/**
 * 候选旁提示，规则固定，避免「有的拼音有的英文」乱跳：
 * - 关翻译：引擎 comment，否则汉字拼音
 * - 开翻译：`拼音 · 英 · 日`（缺啥省啥）；纯英文候选则显示中/日原文
 */
fun CandidateWord.displayComment(): String {
    if (comment.isNotBlank()) return comment

    val prefs = AppPrefs.getInstance().candidateBar
    val latin = text.isNotEmpty() && text.all { it.isLetter() && it.code < 128 }
    val pinyin = if (!latin) PinyinLookup.pinyinOf(text).orEmpty() else ""

    if (!prefs.showTranslationComment.getValue()) {
        return pinyin
    }

    TranslationGloss.ensureLoaded()
    if (latin) {
        return TranslationGloss.sourceOfEnglish(text).orEmpty()
    }
    val gloss = TranslationGloss.entryOf(text)
    val parts = buildList {
        if (pinyin.isNotBlank()) add(pinyin)
        gloss?.en?.takeIf { it.isNotBlank() }?.let { add(it) }
        gloss?.ja?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.joinToString(" · ")
}
