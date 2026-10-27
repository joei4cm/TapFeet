/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.RawConfig
import timber.log.Timber

/**
 * Soft-keyboard typo resilience: turn on common fuzzy sound pairs and keep QWERTY
 * correction for 小企鹅拼音 / 双拼. Adjacent-key mistakes are covered by Correction;
 * z/zh · an/ang 等靠 Fuzzy。
 */
object PinyinFuzzyPrefs {

    private const val FUZZY = "Fuzzy"
    private const val CORRECTION = "Correction"

    /** Common Mandarin fuzzy pairs (engine keys under Fuzzy/). */
    private val COMMON_FUZZY_KEYS = listOf(
        "Z_ZH",
        "C_CH",
        "S_SH",
        "AN_ANG",
        "EN_ENG",
        "IN_ING",
        "IAN_IANG",
        "UAN_UANG",
    )

    fun apply(cfg: RawConfig) {
        val fuzzy = cfg.getOrCreate(FUZZY)
        for (key in COMMON_FUZZY_KEYS) {
            fuzzy.getOrCreate(key).value = "True"
        }
        // Android engine default is Qwerty; re-assert in case a config wiped it.
        fuzzy.getOrCreate(CORRECTION).value = "Qwerty"
    }

    suspend fun FcitxAPI.syncPinyinFuzzyPrefs() {
        for (im in PinyinSpellPrefs.IM_NAMES) {
            val raw = runCatching { getImConfig(im) }.getOrNull()
            val cfg = raw?.findByName("cfg")
            if (cfg == null) {
                Timber.w("IM config missing for %s; skip Fuzzy sync", im)
                continue
            }
            apply(cfg)
            setImConfig(im, cfg)
        }
    }
}
