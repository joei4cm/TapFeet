/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import kotlin.math.abs

/**
 * QWERTY hit testing.
 *
 * Equal-width keys split on the midpoint, so a thumb that lands a little to the right of G
 * (the usual miss on a full keyboard) is scored as H and pinyin goes the wrong way.
 * Letter keys therefore treat a touch to their right as closer than the same distance to
 * their left: the G/H boundary sits inside H, and a tap on G's right side stays G.
 */
object KeyHitResolver {

    /** Distance to the right of a letter center is weighted by this (< 1 = claim more of the next key). */
    const val RIGHT_SIDE_WEIGHT = 0.5f

    /** Don't flip to a neighbor until it wins by this fraction of the current key width. */
    const val SWITCH_MARGIN_FRACTION = 0.12f

    /** How far into the next row (fraction of row height) before a slide leaves the current row. */
    const val ROW_SWITCH_FRACTION = 0.25f

    fun score(touchX: Float, centerX: Float, forgiveRight: Boolean): Float {
        val dx = touchX - centerX
        if (forgiveRight && dx > 0f) return dx * RIGHT_SIDE_WEIGHT
        return abs(dx)
    }

    /**
     * @param stickyIndex index to keep until [SWITCH_MARGIN_FRACTION] of its width says otherwise; -1 on press-down
     */
    fun pick(
        touchX: Float,
        centers: FloatArray,
        widths: FloatArray,
        forgiveRight: BooleanArray,
        stickyIndex: Int = -1,
    ): Int {
        if (centers.isEmpty()) return -1
        var best = 0
        var bestScore = Float.POSITIVE_INFINITY
        val scores = FloatArray(centers.size)
        for (i in centers.indices) {
            val s = score(touchX, centers[i], forgiveRight.getOrElse(i) { false })
            scores[i] = s
            if (s < bestScore) {
                bestScore = s
                best = i
            }
        }
        if (stickyIndex in centers.indices && stickyIndex != best) {
            val margin = widths[stickyIndex] * SWITCH_MARGIN_FRACTION
            if (bestScore + margin >= scores[stickyIndex]) return stickyIndex
        }
        return best
    }

    fun rowIndex(y: Float, height: Int, rowCount: Int, stickyRow: Int?): Int {
        if (height <= 0 || rowCount <= 0) return 0
        val raw = ((y / height) * rowCount).toInt().coerceIn(0, rowCount - 1)
        if (stickyRow == null || stickyRow !in 0 until rowCount || raw == stickyRow) return raw
        val rowH = height.toFloat() / rowCount
        val crossed = if (raw > stickyRow) {
            y - (stickyRow + 1) * rowH
        } else {
            stickyRow * rowH - y
        }
        return if (crossed > rowH * ROW_SWITCH_FRACTION) raw else stickyRow
    }
}
