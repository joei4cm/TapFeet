/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.pinyin.pinyinSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinSegmentsTest {

    @Test
    fun singleRunIsNotABar() {
        assertTrue(pinyinSegments(arrayOf("nihao")).isEmpty())
    }

    @Test
    fun splitsRunsWithCursorOffsets() {
        val segs = pinyinSegments(arrayOf("你好", "世界"))
        assertEquals(2, segs.size)
        assertEquals("你好", segs[0].text)
        assertEquals(0, segs[0].cursor)
        assertEquals("世界", segs[1].text)
        assertEquals(2, segs[1].cursor)
    }
}
