/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry

/**
 * 英文输入法的上屏方式。虚拟键盘与物理键盘共用。
 *
 * [WordHint]：按前缀出单词候选；空格 / 回车上屏已打字母，点候选才换成联想词。
 * [Literal]：打什么字符就上什么字符，不组词、不替换。
 */
enum class EnglishInputMode(
    override val stringRes: Int,
    private val spaceLabelRes: Int,
) : ManagedPreferenceEnum {
    WordHint(R.string.english_input_mode_word_hint, R.string.english_space_label_word_hint),
    Literal(R.string.english_input_mode_literal, R.string.english_space_label_literal);

    companion object {
        const val ENGLISH_IME = "keyboard-us"

        /** 空格键上的短标签：英文态带上屏方式，其他 IME 仍用引擎显示名。 */
        fun spaceBarLabel(context: Context, ime: InputMethodEntry): String {
            if (ime.uniqueName != ENGLISH_IME) return ime.displayName
            return context.getString(
                AppPrefs.getInstance().hardwareKeyboard.englishInputMode.getValue().spaceLabelRes
            )
        }

        fun isEnglishIme(uniqueName: String): Boolean = uniqueName == ENGLISH_IME
    }
}
