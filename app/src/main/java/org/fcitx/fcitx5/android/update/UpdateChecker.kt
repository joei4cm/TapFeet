/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.fcitx.fcitx5.android.utils.Const
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches the latest [UpdateInfo] from GitHub Releases and decides whether an update is available.
 * The "currently installed" version must be passed in by the caller as [currentVersionCode],
 * read at runtime from [android.content.pm.PackageManager] — NOT [android.os.BuildConfig.VERSION_CODE],
 * which is a compile-time constant that stays stale after an in-app update until the process
 * is restarted.
 */
object UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    /** Direct API + common GitHub proxies (same pattern as Rime / voice downloads). */
    private fun githubApiCandidates(apiUrl: String): List<String> = listOf(
        apiUrl,
        "https://ghfast.top/$apiUrl",
        "https://gh-proxy.com/$apiUrl",
    )

    /**
     * Fetch update metadata on an IO dispatcher.
     * Default: query GitHub Releases for [Const.githubUpdateRepo].
     * [overrideUrl] (advanced): a Releases API URL, or a legacy `update.json` URL.
     */
    suspend fun fetchUpdateInfo(overrideUrl: String? = null): Result<UpdateInfo> =
        withContext(Dispatchers.IO) {
            val override = overrideUrl?.trim().orEmpty()
            if (override.isNotEmpty()) {
                return@withContext fetchOverride(override)
            }
            fetchFromGitHubReleases()
        }

    private fun fetchFromGitHubReleases(): Result<UpdateInfo> {
        val latestUrl = Const.githubReleasesLatestApi
        val listUrl = Const.githubReleasesListApi
        var lastError: Throwable? = null

        val latest = fetchText(githubApiCandidates(latestUrl))
            .onFailure { lastError = it }
            .getOrNull()
            ?.let { runCatching { GitHubReleaseUpdate.parseReleaseJson(it) }.getOrNull() }

        val listed = fetchText(githubApiCandidates(listUrl))
            .onFailure { lastError = it }
            .getOrNull()
            ?.let { runCatching { GitHubReleaseUpdate.parseReleaseListJson(it) }.getOrNull() }
            .orEmpty()

        val release = GitHubReleaseUpdate.pickLatestApkRelease(latest, listed)
            ?: return Result.failure(lastError ?: IOException("No GitHub release with TapFeet APK"))
        val info = GitHubReleaseUpdate.toUpdateInfo(release)
            ?: return Result.failure(IOException("Release ${release.tagName} has no tapfeet.ime APK"))
        Timber.d("GitHub release %s → versionCode %d", info.versionName, info.versionCode)
        return Result.success(info)
    }

    private fun fetchOverride(url: String): Result<UpdateInfo> {
        val candidates = if (url.contains("github.com", ignoreCase = true) ||
            url.contains("githubusercontent.com", ignoreCase = true)
        ) {
            githubApiCandidates(url)
        } else {
            listOf(url)
        }
        var lastError: Throwable? = null
        for (candidate in candidates) {
            runCatching {
                val body = httpGet(candidate)
                when {
                    looksLikeReleaseApi(candidate, body) -> {
                        val release = GitHubReleaseUpdate.parseReleaseJson(body)
                        GitHubReleaseUpdate.toUpdateInfo(release)
                            ?: error("No APK asset in release ${release.tagName}")
                    }
                    looksLikeReleaseList(body) -> {
                        val listed = GitHubReleaseUpdate.parseReleaseListJson(body)
                        val release = GitHubReleaseUpdate.pickLatestApkRelease(null, listed)
                            ?: error("No release with TapFeet APK")
                        GitHubReleaseUpdate.toUpdateInfo(release)
                            ?: error("No APK asset")
                    }
                    else -> json.decodeFromString<UpdateInfo>(body)
                }
            }.onSuccess { return Result.success(it) }
                .onFailure {
                    lastError = it
                    Timber.w(it, "Update override fetch failed: %s", candidate)
                }
        }
        return Result.failure(lastError ?: IOException("Override update source failed"))
    }

    private fun looksLikeReleaseApi(url: String, body: String): Boolean {
        if (url.contains("/releases/latest") || url.contains("/releases/tags/")) return true
        val t = body.trimStart()
        return t.startsWith("{") && t.contains("\"tag_name\"") && t.contains("\"assets\"")
    }

    private fun looksLikeReleaseList(body: String): Boolean {
        val t = body.trimStart()
        return t.startsWith("[") && t.contains("\"tag_name\"")
    }

    private fun fetchText(urls: List<String>): Result<String> {
        var lastError: Throwable? = null
        for (url in urls) {
            runCatching { httpGet(url) }
                .onSuccess { return Result.success(it) }
                .onFailure {
                    lastError = it
                    Timber.w(it, "HTTP get failed: %s", url)
                }
        }
        return Result.failure(lastError ?: IOException("All URLs failed"))
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TapFeet-UpdateChecker")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from $url")
            }
            return response.body?.string().orEmpty()
        }
    }

    fun isUpdateAvailable(info: UpdateInfo, currentVersionCode: Long): Boolean =
        info.versionCode > currentVersionCode

    fun isForceUpdate(info: UpdateInfo, currentVersionCode: Long): Boolean =
        info.minVersionCode > currentVersionCode
}
