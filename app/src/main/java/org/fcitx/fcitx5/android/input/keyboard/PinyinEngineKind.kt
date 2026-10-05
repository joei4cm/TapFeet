/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class PinyinEngineKind(
    override val stringRes: Int,
    val uniqueName: String,
) : ManagedPreferenceEnum {
    Fcitx(R.string.pinyin_engine_fcitx, "pinyin"),
    Shuangpin(R.string.pinyin_engine_shuangpin, "shuangpin"),
    Rime(R.string.pinyin_engine_rime, "rime"),
    Wubi(R.string.pinyin_engine_wubi, "wbx");
}
