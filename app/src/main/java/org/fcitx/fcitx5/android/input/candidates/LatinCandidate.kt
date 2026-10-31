/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.candidates

import org.fcitx.fcitx5.android.core.CandidateWord

/**
 * 拼音 / 双拼 / 中州韵 / 五笔只出中文候选。纯拉丁单词（含 don't、e-mail）留给英文输入法。
 * 选词下标仍是引擎原始下标，隐藏英文后点选不会错位。
 */
object LatinCandidate {
    private val chineseIme = setOf("pinyin", "shuangpin", "rime", "wbx")

    fun hidesInIme(uniqueName: String): Boolean = uniqueName in chineseIme

    /** 整段都是拉丁字母，允许词中的 `'` / `-`。中文、数字、符号都不是英文单词候选。 */
    fun isEnglishWord(text: String): Boolean {
        if (text.isEmpty()) return false
        var letters = 0
        for (c in text) {
            when (c) {
                in 'A'..'Z', in 'a'..'z' -> letters++
                '\'', '-' -> if (letters == 0) return false
                else -> return false
            }
        }
        return letters > 0
    }

    class Dropped(
        val words: Array<CandidateWord>,
        val engineIndices: IntArray,
    )

    fun drop(
        words: Array<CandidateWord>,
        hideEnglish: Boolean,
        indexBase: Int = 0,
    ): Dropped {
        if (!hideEnglish || words.isEmpty()) {
            return Dropped(words, IntArray(words.size) { indexBase + it })
        }
        val kept = ArrayList<CandidateWord>(words.size)
        val indices = ArrayList<Int>(words.size)
        for (i in words.indices) {
            if (!isEnglishWord(words[i].text)) {
                kept += words[i]
                indices += indexBase + i
            }
        }
        return Dropped(kept.toTypedArray(), indices.toIntArray())
    }

    /** 分页光标是过滤前的下标。落在被丢掉的英文词上时，改高亮过滤后的第一个。 */
    fun displayCursor(original: Int, engineIndices: IntArray): Int {
        if (engineIndices.isEmpty()) return -1
        if (original < 0) return 0
        val at = engineIndices.indexOf(original)
        return if (at >= 0) at else 0
    }
}
