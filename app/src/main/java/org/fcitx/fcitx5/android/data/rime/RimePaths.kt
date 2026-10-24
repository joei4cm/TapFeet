/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

import org.fcitx.fcitx5.android.utils.appContext
import java.io.File

/** fcitx5-rime 用户目录，与手册「手动导入」路径一致。 */
object RimePaths {
    fun userDir(): File =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "data/rime").also {
            it.mkdirs()
        }

    fun githubMirrors(url: String): List<String> = listOf(
        url,
        "https://ghfast.top/$url",
        "https://gh-proxy.com/$url",
    )
}
