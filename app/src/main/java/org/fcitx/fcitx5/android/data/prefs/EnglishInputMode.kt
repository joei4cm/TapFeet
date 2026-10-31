/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

import org.fcitx.fcitx5.android.R

/**
 * 英文输入法的上屏方式。虚拟键盘与物理键盘共用。
 *
 * [WordHint]：按前缀出单词候选；空格 / 回车上屏已打字母，点候选才换成联想词。
 * [Literal]：打什么字符就上什么字符，不组词、不替换。
 */
enum class EnglishInputMode(override val stringRes: Int) : ManagedPreferenceEnum {
    WordHint(R.string.english_input_mode_word_hint),
    Literal(R.string.english_input_mode_literal),
}
