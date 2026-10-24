/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RimePackUrlsTest {

    @Test
    fun prefersTapFeetHostsBeforeUpstream() {
        val upstream = "https://github.com/iDvel/rime-ice/releases/download/2026.06.30/full_compiled.zip"
        val urls = RimePackUrls.candidates(RimePackUrls.ASSET_ICE, upstream)
        assertEquals(
            "https://gitee.com/zziloong/TapFeet/releases/download/rime-packs/rime-ice-full_compiled.zip",
            urls[0]
        )
        assertEquals(
            "https://github.com/joei4cm/TapFeet/releases/download/rime-packs/rime-ice-full_compiled.zip",
            urls[1]
        )
        assertEquals(upstream, urls[2])
        assertTrue(urls.any { it.startsWith("https://ghfast.top/") })
    }

    @Test
    fun nonGithubUpstreamIsNotWrappedInGhProxy() {
        val cnb = "https://cnb.cool/Mintimate/rime/oh-my-rime/-/releases/download/latest/oh-my-rime.zip"
        val urls = RimePackUrls.candidates(RimePackUrls.ASSET_MINT, cnb)
        assertEquals(cnb, urls[2])
        assertTrue(urls.none { it.startsWith("https://ghfast.top/$cnb") })
    }
}
