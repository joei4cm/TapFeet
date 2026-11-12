/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.core.FcitxAPI

/**
 * Soft 26-key typo session: accumulate latin, optionally rewrite via [SoftPinyinTypo.recover]
 * (transpose / extra key), then sync into fcitx — same pattern as [T9Session] for nine-key.
 *
 * Only active while [TextKeyboard] is attached. Never run on nine-key or hardware-only paths.
 */
object SoftTypoSession {

    private val letters = StringBuilder()
    private var lastSent = ""
    @Volatile
    private var writing = false
    @Volatile
    private var active = false

    fun setActive(value: Boolean) {
        active = value
        if (!value) clear()
    }

    fun clear() {
        letters.setLength(0)
        lastSent = ""
    }

    fun hasPending(): Boolean = letters.isNotEmpty()

    fun onPreeditEmpty(empty: Boolean) {
        if (!active || writing || !empty) return
        clear()
    }

    fun onPanelLatin(text: String) {
        if (!active || writing) return
        val latin = buildString {
            for (c in text) if (c.isLetter()) append(c.lowercaseChar())
        }
        if (latin.isEmpty()) return
        letters.setLength(0)
        letters.append(latin)
        lastSent = latin
    }

    /**
     * @return true if this letter was consumed (already synced into fcitx).
     */
    suspend fun onLetter(ch: Char, chinese: Boolean, fcitx: FcitxAPI): Boolean {
        if (!active || !chinese) return false
        val c = ch.lowercaseChar()
        if (c !in 'a'..'z') return false
        if (fcitx.isEmpty() && letters.isNotEmpty()) {
            letters.setLength(0)
            lastSent = ""
        }
        letters.append(c)
        sync(fcitx)
        return true
    }

    suspend fun onBackspace(fcitx: FcitxAPI): Boolean {
        if (!active || letters.isEmpty()) return false
        letters.deleteCharAt(letters.lastIndex)
        sync(fcitx)
        return true
    }

    private suspend fun sync(fcitx: FcitxAPI) {
        val raw = letters.toString()
        val pinyin = if (raw.isEmpty()) "" else SoftPinyinTypo.recover(raw)
        // Keep buffer aligned with what we consider the user meant after recovery,
        // so further transpose decisions build on the corrected string.
        if (pinyin != raw && pinyin.isNotEmpty()) {
            letters.setLength(0)
            letters.append(pinyin)
        }
        writing = true
        try {
            if (pinyin == lastSent) return
            if (pinyin.startsWith(lastSent) && lastSent.isNotEmpty()) {
                for (c in pinyin.substring(lastSent.length)) {
                    fcitx.sendKey(c)
                }
            } else {
                fcitx.reset()
                for (c in pinyin) {
                    fcitx.sendKey(c)
                }
            }
            lastSent = pinyin
        } finally {
            writing = false
        }
    }

    /** Test helper: run recover path without fcitx. */
    fun preview(raw: String): String = SoftPinyinTypo.recover(raw)
}
