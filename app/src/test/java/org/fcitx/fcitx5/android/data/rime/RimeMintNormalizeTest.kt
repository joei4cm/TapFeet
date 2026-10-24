/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RimeMintNormalizeTest {

    @Test
    fun stripsGithubArchivePrefix() {
        assertEquals(
            "rime_mint.schema.yaml",
            RimeMintZipPaths.normalizeEntryName(
                "oh-my-rime-5fca6ae067fd666b5ec4f119c5147efd629cc76d/rime_mint.schema.yaml",
            ),
        )
        assertEquals(
            "dicts/rime_mint.base.dict.yaml",
            RimeMintZipPaths.normalizeEntryName("oh-my-rime/dicts/rime_mint.base.dict.yaml"),
        )
    }

    @Test
    fun keepsFlatCnbPaths() {
        assertEquals(
            "rime_mint.schema.yaml",
            RimeMintZipPaths.normalizeEntryName("rime_mint.schema.yaml"),
        )
    }

    @Test
    fun dropsArchiveRootDir() {
        assertNull(RimeMintZipPaths.normalizeEntryName("oh-my-rime-5fca6ae0/"))
        assertNull(RimeMintZipPaths.normalizeEntryName("oh-my-rime-5fca6ae0"))
    }

    @Test
    fun skipsJunk() {
        assertTrue(RimeMintZipPaths.shouldSkipEntry(".github/workflows/x.yml"))
        assertTrue(RimeMintZipPaths.shouldSkipEntry("README.md"))
        assertTrue(RimeMintZipPaths.shouldSkipEntry("demo.webp"))
        assertFalse(RimeMintZipPaths.shouldSkipEntry("rime_mint.schema.yaml"))
    }
}
