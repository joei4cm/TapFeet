/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.RawConfig
import timber.log.Timber

/**
 * Soft-keyboard typo resilience for 小企鹅拼音 / 双拼.
 *
 * Keep nasal fuzzy (an/ang …) and QWERTY adjacent-key Correction. Do **not** force
 * z↔zh / c↔ch / s↔sh — those make exact input like「总是字符输入出错」里的 zi/shi/chu
 * compete with zhi/si/cu，空格首选容易上错字。
 */
object PinyinFuzzyPrefs {

    private const val FUZZY = "Fuzzy"
    private const val CORRECTION = "Correction"

    /** Nasal / rhyme pairs that help soft typos without rewriting initials. */
    private val NASAL_FUZZY_KEYS = listOf(
        "AN_ANG",
        "EN_ENG",
        "IN_ING",
        "IAN_IANG",
        "UAN_UANG",
    )

    /**
     * Initial pairs we used to force on; leave engine default (false) so exact pinyin wins.
     * Explicit False clears installs that already synced True from older builds.
     */
    private val INITIAL_FUZZY_KEYS = listOf(
        "Z_ZH",
        "C_CH",
        "S_SH",
    )

    fun apply(cfg: RawConfig) {
        val fuzzy = cfg.getOrCreate(FUZZY)
        for (key in NASAL_FUZZY_KEYS) {
            fuzzy.getOrCreate(key).value = "True"
        }
        for (key in INITIAL_FUZZY_KEYS) {
            fuzzy.getOrCreate(key).value = "False"
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
