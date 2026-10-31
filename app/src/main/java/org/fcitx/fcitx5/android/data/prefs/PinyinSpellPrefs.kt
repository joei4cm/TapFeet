/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.RawConfig
import timber.log.Timber

/**
 * 拼音 / 双拼的 `SpellEnabled` 固定关掉。
 * 英文单词只在英文输入法里联想；拼音候选不再夹英文。
 */
object PinyinSpellPrefs {
    const val SPELL_ENABLED = "SpellEnabled"

    /** 小企鹅拼音与双拼共用 pinyin addon，各自一份 IM 配置。 */
    val IM_NAMES = listOf("pinyin", "shuangpin")

    fun apply(cfg: RawConfig) {
        cfg.getOrCreate(SPELL_ENABLED).value = "False"
    }

    suspend fun FcitxAPI.syncPinyinSpellPrefs() {
        for (im in IM_NAMES) {
            val raw = runCatching { getImConfig(im) }.getOrNull()
            val cfg = raw?.findByName("cfg")
            if (cfg == null) {
                Timber.w("IM config missing for %s; skip SpellEnabled sync", im)
                continue
            }
            apply(cfg)
            setImConfig(im, cfg)
        }
    }
}
