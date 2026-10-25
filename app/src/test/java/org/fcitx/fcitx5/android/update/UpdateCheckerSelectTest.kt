/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.update

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
}
