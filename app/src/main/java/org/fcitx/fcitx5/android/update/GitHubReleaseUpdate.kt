/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses GitHub Releases API payloads into [UpdateInfo].
 * Version codes match [Versions.calculateVersionCode] (arm64-v8a) in build-logic.
 */
internal object GitHubReleaseUpdate {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val apkNameRegex = Regex("""^tapfeet\.ime-v?\d.*\.apk$""", RegexOption.IGNORE_CASE)
    private val versionCodeRegex = Regex("""[vV]?(\d+)\.(\d+)\.(\d+)(?:-(\d+))?""")

    @Serializable
    data class Release(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )

    fun parseReleaseJson(body: String): Release = json.decodeFromString(body)

    fun parseReleaseListJson(body: String): List<Release> = json.decodeFromString(body)

    /** Prefer `/releases/latest`; otherwise first non-draft release that has a TapFeet APK. */
    fun pickLatestApkRelease(latest: Release?, listed: List<Release>): Release? {
        if (latest != null && !latest.draft && toUpdateInfo(latest) != null) return latest
        return listed.firstOrNull { !it.draft && !it.prerelease && toUpdateInfo(it) != null }
            ?: listed.firstOrNull { !it.draft && toUpdateInfo(it) != null }
    }

    fun toUpdateInfo(release: Release): UpdateInfo? {
        val asset = release.assets.firstOrNull { apkNameRegex.matches(it.name.trim()) }
            ?: return null
        val versionName = normalizeVersionName(release.tagName.ifBlank { release.name })
        val versionCode = versionCodeArm64(versionName) ?: return null
        val notes = release.body.orEmpty()
            .lineSequence()
            .map { it.trimEnd() }
            .takeWhile { !it.trimStart().startsWith("---") }
            .joinToString("\n")
            .trim()
        val publishDate = release.publishedAt?.take(10).orEmpty()
        return UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            downloadUrl = asset.browserDownloadUrl,
            releaseNotes = notes,
            minVersionCode = 0,
            publishDate = publishDate,
        )
    }

    fun normalizeVersionName(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return t
        return if (t.startsWith("v") || t.startsWith("V")) "v${t.drop(1)}" else "v$t"
    }

    /** Same formula as build-logic [Versions.calculateVersionCode] for arm64-v8a (abiId=2). */
    fun versionCodeArm64(versionName: String): Int? {
        val m = versionCodeRegex.find(versionName) ?: return null
        val major = m.groupValues[1].toInt()
        val minor = m.groupValues[2].toInt()
        val patch = m.groupValues[3].toInt()
        val build = m.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val base = major * 100_000 + minor * 1_000 + patch * 10 + build
        return base * 10 + 2
    }
}
