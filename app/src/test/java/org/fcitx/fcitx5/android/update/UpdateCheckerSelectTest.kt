/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerSelectTest {

    @Test
    fun newerRemoteVersionIsAvailable() {
        val remote = UpdateInfo(
            versionCode = 1003702,
            versionName = "v1.0.37",
            downloadUrl = "https://example.com/app.apk",
        )
        assertTrue(UpdateChecker.isUpdateAvailable(remote, currentVersionCode = 1003602))
        assertFalse(UpdateChecker.isUpdateAvailable(remote, currentVersionCode = 1003702))
    }

    @Test
    fun staleMirrorMustNotHideNewerCandidate() {
        // Simulate what fetchUpdateInfo does: keep the max versionCode across sources.
        val giteeStale = UpdateInfo(1001502, "v1.0.15", "https://gitee.example/old.apk")
        val githubFresh = UpdateInfo(1003702, "v1.0.37", "https://github.example/new.apk")
        val best = listOf(giteeStale, githubFresh).maxBy { it.versionCode }
        assertEquals("v1.0.37", best.versionName)
        assertTrue(UpdateChecker.isUpdateAvailable(best, currentVersionCode = 1003602))
    }
}
