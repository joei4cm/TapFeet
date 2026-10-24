/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum
import org.fcitx.fcitx5.android.data.rime.RimeSchemaSwitcher

enum class PinyinEngineKind(
    override val stringRes: Int,
    val uniqueName: String,
    /** 非空时表示走内置 fcitx5-rime，并切换到该 schema。 */
    val rimeSchemaId: String? = null,
    /** 需先联网下载方案包（雾凇 / 白霜）。 */
    val needsDownload: Boolean = false,
) : ManagedPreferenceEnum {
    Fcitx(R.string.pinyin_engine_fcitx, "pinyin"),
    Shuangpin(R.string.pinyin_engine_shuangpin, "shuangpin"),
    Rime(
        R.string.pinyin_engine_rime,
        "rime",
        rimeSchemaId = RimeSchemaSwitcher.LUNA_SIMP,
    ),
    RimeIce(
        R.string.pinyin_engine_rime_ice,
        "rime",
        rimeSchemaId = RimeSchemaSwitcher.RIME_ICE,
        needsDownload = true,
    ),
    RimeFrost(
        R.string.pinyin_engine_rime_frost,
        "rime",
        rimeSchemaId = RimeSchemaSwitcher.RIME_FROST,
        needsDownload = true,
    ),
    Wubi(R.string.pinyin_engine_wubi, "wbx");
}
