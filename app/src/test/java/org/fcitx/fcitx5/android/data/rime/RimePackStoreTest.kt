/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RimePackStoreTest {

    @Test
    fun parseActiveMarker() {
        assertEquals("ice" to "2026.06.30", parse("ice\n2026.06.30\n"))
        assertEquals("mint" to "2025.10.19-cnb", parse("mint\n2025.10.19-cnb"))
        assertNull(parse(""))
        assertNull(parse("ice"))
    }

    private fun parse(text: String): Pair<String, String>? {
        val lines = text.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        return lines[0] to lines[1]
    }
}
