/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

/**
 * 虚拟键盘主布局：26 键全键盘或 9 键。实体键盘机与触摸机共用；设了就记住。
 */
enum class VirtualLayout(override val stringRes: Int) : ManagedPreferenceEnum {
    Qwerty(R.string.virtual_layout_qwerty),
    NineKey(R.string.virtual_layout_nine_key);

    val keyboardName: String
        get() = if (this == NineKey) NineKeyKeyboard.Name else TextKeyboard.Name

    companion object {
        fun fromKeyboardName(name: String): VirtualLayout {
            return if (name == NineKeyKeyboard.Name) NineKey else Qwerty
        }

        fun isTextLayout(name: String): Boolean {
            return name == TextKeyboard.Name || name == NineKeyKeyboard.Name
        }
    }
}
