/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.candidates

import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.pinyin.PinyinLookup

/** Engine comment, or the pinyin of the candidate when the engine left comment empty. */
fun CandidateWord.displayComment(): String {
    if (comment.isNotBlank()) return comment
    return PinyinLookup.pinyinOf(text).orEmpty()
}
