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
 *
 * 仅在九键布局 [active] 时同步预编辑 / 劫持退格。26 键字母是确定的，绝不能走 T9 重解码
 *（否则删 `hao` 的 `o` 时，pad `42` 可能被解成 `ga` 而不是留下 `ha`）。
 */
object T9Session {

    private const val MULTI_TAP_MS = 800L

    private val digits = StringBuilder()
    private var lastPinyin = ""
    @Volatile
    private var writing = false
    @Volatile
    private var active = false

    private var lastEnglishDigit: Char? = null
    private var lastEnglishTapAt = 0L
    private var englishCycle = 0

    /** 九键布局附着时打开；离开九键时关闭并清空，避免污染 26 键退格。 */
    fun setActive(value: Boolean) {
        active = value
        if (!value) clear()
    }

    fun clear() {
        digits.setLength(0)
        lastPinyin = ""
        lastEnglishDigit = null
        englishCycle = 0
    }

    /** 测试与诊断：当前是否积压了待重解的九键数字串。 */
    fun hasPendingDigits(): Boolean = digits.isNotEmpty()

    fun onPreeditEmpty(empty: Boolean) {
        if (!active || writing || !empty) return
        clear()
    }

    fun onPanelLatin(text: String) {
        if (!active || writing) return
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
        if (!active) return
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
        if (!active || digits.isEmpty()) return false
        digits.deleteCharAt(digits.lastIndex)
        syncChinese(fcitx)
        return true
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
