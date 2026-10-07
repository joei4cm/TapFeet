/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.RawConfig
import timber.log.Timber

/**
 * 把「拼音中显示英文候选」同步到小企鹅拼音 / 双拼的 `SpellEnabled`。
 * 引擎侧已有 Spell；这里只负责设置开关落盘到 IM 配置。
 */
object PinyinSpellPrefs {
    const val SPELL_ENABLED = "SpellEnabled"

    /** 小企鹅拼音与双拼共用 pinyin addon，各自一份 IM 配置。 */
    val IM_NAMES = listOf("pinyin", "shuangpin")

    fun apply(cfg: RawConfig, enabled: Boolean) {
        cfg.getOrCreate(SPELL_ENABLED).value = if (enabled) "True" else "False"
    }

    suspend fun FcitxAPI.syncPinyinSpellPrefs() {
        val enabled = AppPrefs.getInstance().candidateBar.pinyinEnglishCandidates.getValue()
        for (im in IM_NAMES) {
            val raw = runCatching { getImConfig(im) }.getOrNull()
            val cfg = raw?.findByName("cfg")
            if (cfg == null) {
                Timber.w("IM config missing for %s; skip SpellEnabled sync", im)
                continue
            }
            apply(cfg, enabled)
            setImConfig(im, cfg)
        }
    }
}
