/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.RawConfig
import timber.log.Timber

/**
 * Maps TapFeet English-completion prefs onto the `androidkeyboard` addon config.
 *
 * Prefix completion (spell `en_dict.fscd`) plus optional auto-space after picking a word.
 * This is not Gboard-style next-word prediction.
 */
object AndroidKeyboardPrefs {
    const val ADDON_NAME = "androidkeyboard"
    const val ENABLE_WORD_HINT = "EnableWordHint"
    const val INSERT_SPACE = "InsertSpace"

    fun apply(cfg: RawConfig, wordHint: Boolean, insertSpace: Boolean) {
        cfg.getOrCreate(ENABLE_WORD_HINT).value = boolValue(wordHint)
        cfg.getOrCreate(INSERT_SPACE).value = boolValue(insertSpace)
    }

    fun applyFromHardwareKeyboard(
        cfg: RawConfig,
        hw: AppPrefs.HardwareKeyboard = AppPrefs.getInstance().hardwareKeyboard,
    ) {
        val wordHint = hw.englishWordHint.getValue()
        apply(cfg, wordHint, wordHint && hw.englishInsertSpace.getValue())
    }

    suspend fun FcitxAPI.syncAndroidKeyboardPrefs() {
        val raw = getAddonConfig(ADDON_NAME)
        val cfg = raw.findByName("cfg")
        if (cfg == null) {
            Timber.w("androidkeyboard config missing; skip English word-hint sync")
            return
        }
        applyFromHardwareKeyboard(cfg)
        setAddonConfig(ADDON_NAME, cfg)
    }

    private fun boolValue(value: Boolean) = if (value) "True" else "False"
}
