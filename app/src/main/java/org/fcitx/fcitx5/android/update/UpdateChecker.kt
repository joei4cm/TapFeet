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
 * Fetches the latest [UpdateInfo] over HTTPS and decides whether an update is available.
 * The "currently installed" version must be passed in by the caller as [currentVersionCode],
 * read at runtime from [android.content.pm.PackageManager] — NOT [android.os.BuildConfig.VERSION_CODE],
 * which is a compile-time constant that stays stale after an in-app update until the process
 * is restarted (the running settings process keeps reporting the OLD version, so an update would
 * look perpetually available).
 */
object UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Plain direct connection (no system proxy). The app targets a mainland-China audience
    // and reaches Gitee / jsDelivr directly, so we don't honor the Wi-Fi/manual proxy.
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Candidate metadata URLs. Stale mirrors (e.g. Gitee stuck on an old release) must not win
     * just because they answer first — [fetchUpdateInfo] keeps the highest [UpdateInfo.versionCode].
     */
    private fun candidateUrls(): List<String> = buildList {
        add(Const.updateInfoUrlGithub)
        add(Const.updateInfoUrlJsDelivr)
        add(Const.updateInfoUrlGitee)
        // Gitee default branch may be main or master.
        val gitee = Const.updateInfoUrlGitee
        if (gitee.contains("/raw/main/")) {
            add(gitee.replace("/raw/main/", "/raw/master/"))
        } else if (gitee.contains("/raw/master/")) {
            add(gitee.replace("/raw/master/", "/raw/main/"))
        }
    }.distinct()

    /**
     * Fetch update metadata on an IO dispatcher.
     * When [overrideUrl] is provided (advanced setting), only that URL is tried; otherwise every
     * [candidateUrls] entry is tried and the payload with the highest [UpdateInfo.versionCode] wins.
     */
    suspend fun fetchUpdateInfo(overrideUrl: String? = null): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val urls = if (!overrideUrl.isNullOrBlank()) listOf(overrideUrl.trim()) else candidateUrls()
        var best: UpdateInfo? = null
        var lastError: Throwable? = null
        for (url in urls) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} from $url")
                    }
                    val body = response.body?.string().orEmpty()
                    json.decodeFromString<UpdateInfo>(body)
                }
            }.onSuccess { info ->
                val prev = best
                if (prev == null || info.versionCode > prev.versionCode) {
                    best = info
                    Timber.d("Update metadata from %s → %s (%d)", url, info.versionName, info.versionCode)
                }
            }.onFailure {
                lastError = it
                Timber.w(it, "Update metadata fetch failed: %s", url)
            }
        }
        best?.let { Result.success(it) }
            ?: Result.failure(lastError ?: IOException("All update sources failed"))
    }

    fun isUpdateAvailable(info: UpdateInfo, currentVersionCode: Long): Boolean =
        info.versionCode > currentVersionCode

    fun isForceUpdate(info: UpdateInfo, currentVersionCode: Long): Boolean =
        info.minVersionCode > currentVersionCode
}
