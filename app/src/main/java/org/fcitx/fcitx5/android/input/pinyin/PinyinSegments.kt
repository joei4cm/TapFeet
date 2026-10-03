/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.pinyin

data class PinyinSegment(val text: String, val cursor: Int)

/**
 * Clickable preedit pieces for a long pinyin sentence. Each run is one segment; [PinyinSegment.cursor]
 * is the Java-char index at the start of that run (callers convert to fcitx's UTF-8 code-point
 * cursor via [org.fcitx.fcitx5.android.core.FormattedText.codePointCountUntil]).
 */
fun pinyinSegments(runs: Array<String>): List<PinyinSegment> {
    if (runs.size <= 1) return emptyList()
    var cursor = 0
    val out = ArrayList<PinyinSegment>(runs.size)
    for (s in runs) {
        if (s.isNotEmpty()) out += PinyinSegment(s, cursor)
        cursor += s.length
    }
    return if (out.size <= 1) emptyList() else out
}
