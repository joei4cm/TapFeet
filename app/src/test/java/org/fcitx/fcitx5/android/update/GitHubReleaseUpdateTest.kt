/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdateTest {

    @Test
    fun versionCodeMatchesBuildLogicArm64() {
        assertEquals(1003702, GitHubReleaseUpdate.versionCodeArm64("v1.0.37"))
        assertEquals(1003602, GitHubReleaseUpdate.versionCodeArm64("1.0.36"))
        assertEquals(1001502, GitHubReleaseUpdate.versionCodeArm64("v1.0.15"))
    }

    @Test
    fun parsesLatestReleaseWithApk() {
        val body = """
            {
              "tag_name": "v1.0.37",
              "name": "TapFeet v1.0.37",
              "body": "- fix A\n- fix B\n\n---\nSigned APK",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-10-25T09:07:23Z",
              "assets": [
                {
                  "name": "tapfeet.ime-v1.0.37.apk",
                  "browser_download_url": "https://github.com/joei4cm/TapFeet/releases/download/v1.0.37/tapfeet.ime-v1.0.37.apk"
                }
              ]
            }
        """.trimIndent()
        val release = GitHubReleaseUpdate.parseReleaseJson(body)
        val info = GitHubReleaseUpdate.toUpdateInfo(release)
        assertNotNull(info)
        assertEquals("v1.0.37", info!!.versionName)
        assertEquals(1003702, info.versionCode)
        assertTrue(info.downloadUrl.endsWith("tapfeet.ime-v1.0.37.apk"))
        assertTrue(info.releaseNotes.contains("fix A"))
        assertEquals("2026-10-25", info.publishDate)
    }

    @Test
    fun skipsRimePacksReleaseWithoutApk() {
        val body = """
            {
              "tag_name": "rime-packs",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "rime-ice-full_compiled.zip", "browser_download_url": "https://example/z.zip"}
              ]
            }
        """.trimIndent()
        val release = GitHubReleaseUpdate.parseReleaseJson(body)
        assertNull(GitHubReleaseUpdate.toUpdateInfo(release))
    }

    @Test
    fun pickLatestSkipsNonApkThenTakesApk() {
        val rime = GitHubReleaseUpdate.parseReleaseJson(
            """{"tag_name":"rime-packs","assets":[{"name":"x.zip","browser_download_url":"https://e/x.zip"}]}"""
        )
        val app = GitHubReleaseUpdate.parseReleaseJson(
            """
            {"tag_name":"v1.0.36","assets":[
              {"name":"tapfeet.ime-v1.0.36.apk","browser_download_url":"https://e/a.apk"}
            ]}
            """.trimIndent()
        )
        val picked = GitHubReleaseUpdate.pickLatestApkRelease(rime, listOf(rime, app))
        assertEquals("v1.0.36", picked!!.tagName)
    }
}
