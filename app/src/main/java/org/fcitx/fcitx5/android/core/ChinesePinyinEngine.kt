/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.core

import timber.log.Timber

/**
 * Swap the Chinese IM in the enabled list between fcitx pinyin and bundled Rime.
 * English (`keyboard-us`) and any other IMs stay put.
 */
object ChinesePinyinEngine {
    const val PINYIN = "pinyin"
    const val RIME = "rime"

    fun applyEnabled(enabled: List<String>, useRime: Boolean): List<String> {
        val chinese = if (useRime) RIME else PINYIN
        val idx = enabled.indexOfFirst { it == PINYIN || it == RIME }
        val others = enabled.filter { it != PINYIN && it != RIME }
        if (idx < 0) return listOf(chinese) + others
        return others.toMutableList().also { it.add(idx.coerceAtMost(it.size), chinese) }
    }

    fun shouldActivate(current: String, useRime: Boolean): String? {
        val chinese = if (useRime) RIME else PINYIN
        val other = if (useRime) PINYIN else RIME
        return chinese.takeIf { current == other }
    }
}

suspend fun FcitxAPI.syncChinesePinyinEngine(useRime: Boolean) {
    if (useRime && availableIme().none { it.uniqueName == ChinesePinyinEngine.RIME }) {
        Timber.w("rime input method is not available")
        return
    }
    val enabled = enabledIme().map { it.uniqueName }
    val next = ChinesePinyinEngine.applyEnabled(enabled, useRime)
    if (next != enabled) {
        setEnabledIme(next.toTypedArray())
    }
    ChinesePinyinEngine.shouldActivate(currentIme().uniqueName, useRime)?.let { activateIme(it) }
}
