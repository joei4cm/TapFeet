/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

/**
 * 雾凇 / 白霜 / 薄荷方案包下载地址。
 *
 * 优先走 TapFeet 自建 release（Gitee → GitHub `rime-packs` 标签），再回落上游。
 * 镜像资产由 `.github/workflows/mirror-rime-packs.yml` 从上游同步。
 */
object RimePackUrls {

    const val RELEASE_TAG = "rime-packs"

    private const val GITEE_BASE =
        "https://gitee.com/zziloong/TapFeet/releases/download/$RELEASE_TAG"
    private const val GITHUB_BASE =
        "https://github.com/joei4cm/TapFeet/releases/download/$RELEASE_TAG"

    const val ASSET_ICE = "rime-ice-full_compiled.zip"
    const val ASSET_FROST = "rime-frost-schemas.zip"
    const val ASSET_MINT = "oh-my-rime.zip"

    /**
     * 自建镜像在前，[upstream] 及其 GitHub 加速镜像在后。
     * 非 github.com 的上游 URL 只追加一次（如 cnb.cool）。
     */
    fun candidates(asset: String, vararg upstream: String): List<String> = buildList {
        add("$GITEE_BASE/$asset")
        add("$GITHUB_BASE/$asset")
        for (url in upstream) {
            if (url.contains("github.com", ignoreCase = true)) {
                addAll(RimePaths.githubMirrors(url))
            } else {
                add(url)
            }
        }
    }
}
