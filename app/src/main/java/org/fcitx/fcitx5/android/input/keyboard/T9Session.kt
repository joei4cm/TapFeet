/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.os.SystemClock
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym

/**
 * 9 键会话。中文走 [T9Pinyin] 整串重解再写入 fcitx；英文多击出字母，交给现有英文联想。
 */
object T9Session {

    private const val MULTI_TAP_MS = 800L

    private val digits = StringBuilder()
    private var lastPinyin = ""
    @Volatile
    private var writing = false

    private var lastEnglishDigit: Char? = null
    private var lastEnglishTapAt = 0L
    private var englishCycle = 0

    fun clear() {
        digits.setLength(0)
        lastPinyin = ""
        lastEnglishDigit = null
        englishCycle = 0
    }

    fun onPreeditEmpty(empty: Boolean) {
        if (writing || !empty) return
        clear()
    }

    fun onPanelLatin(text: String) {
        if (writing) return
        val latin = buildString {
            for (c in text) {
                if (c.isLetter()) append(c.lowercaseChar())
            }
        }
        if (latin.isEmpty()) return
        val d = T9Pinyin.digitsOfLatin(latin)
        if (d.isEmpty()) return
        digits.setLength(0)
        digits.append(d)
        lastPinyin = latin
    }

    suspend fun onDigit(digit: Char, chinese: Boolean, fcitx: FcitxAPI) {
        if (digit !in '2'..'9') return
        if (chinese) {
            lastEnglishDigit = null
            onChineseDigit(digit, fcitx)
        } else {
            digits.setLength(0)
            lastPinyin = ""
            onEnglishDigit(digit, fcitx)
        }
    }

    suspend fun onBackspace(fcitx: FcitxAPI): Boolean {
        if (digits.isNotEmpty()) {
            digits.deleteCharAt(digits.lastIndex)
            syncChinese(fcitx)
            return true
        }
        return false
    }

    private suspend fun onChineseDigit(digit: Char, fcitx: FcitxAPI) {
        if (fcitx.isEmpty() && digits.isNotEmpty()) {
            digits.setLength(0)
            lastPinyin = ""
        }
        digits.append(digit)
        syncChinese(fcitx)
    }

    private suspend fun syncChinese(fcitx: FcitxAPI) {
        val pinyin = T9Pinyin.decode(digits.toString())
        writing = true
        try {
            if (pinyin == lastPinyin) return
            if (pinyin.startsWith(lastPinyin) && lastPinyin.isNotEmpty()) {
                for (c in pinyin.substring(lastPinyin.length)) {
                    fcitx.sendKey(c)
                }
            } else {
                fcitx.reset()
                for (c in pinyin) {
                    fcitx.sendKey(c)
                }
            }
            lastPinyin = pinyin
        } finally {
            writing = false
        }
    }

    private suspend fun onEnglishDigit(digit: Char, fcitx: FcitxAPI) {
        val letters = T9Pinyin.lettersByDigit[digit] ?: return
        val now = SystemClock.uptimeMillis()
        val sameKey = digit == lastEnglishDigit && now - lastEnglishTapAt < MULTI_TAP_MS
        if (sameKey) {
            englishCycle = (englishCycle + 1) % letters.length
            fcitx.sendKey(
                KeySym(FcitxKeyMapping.FcitxKey_BackSpace),
                KeyStates.Virtual,
            )
        } else {
            englishCycle = 0
        }
        lastEnglishDigit = digit
        lastEnglishTapAt = now
        fcitx.sendKey(letters[englishCycle])
    }
}
