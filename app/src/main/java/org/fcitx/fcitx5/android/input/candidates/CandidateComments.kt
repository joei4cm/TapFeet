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
 * 候选旁提示：引擎 comment →（可选）本地中/日英释义 → 汉字拼音。
 */
fun CandidateWord.displayComment(): String {
    if (comment.isNotBlank()) return comment
    if (AppPrefs.getInstance().candidateBar.showTranslationComment.getValue()) {
        TranslationGloss.ensureLoaded()
        TranslationGloss.glossOf(text)?.let { return it }
        // 英文候选：反查中/日原文作提示
        if (text.isNotEmpty() && text.all { it.isLetter() && it.code < 128 }) {
            TranslationGloss.sourceOfEnglish(text)?.let { return it }
        }
    }
    return PinyinLookup.pinyinOf(text).orEmpty()
}
