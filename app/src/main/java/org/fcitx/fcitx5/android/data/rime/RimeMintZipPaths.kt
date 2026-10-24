/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

/** oh-my-rime zip 路径规范化（CNB 扁平 / GitHub archive 带前缀）。纯逻辑，可单测。 */
object RimeMintZipPaths {

    fun normalizeEntryName(raw: String): String? {
        var n = raw.removePrefix("./")
        if (n.isEmpty()) return null
        if (n.startsWith("oh-my-rime-") || n == "oh-my-rime" || n.startsWith("oh-my-rime/")) {
            val slash = n.indexOf('/')
            if (slash < 0) return null
            n = n.substring(slash + 1)
        }
        return n.takeIf { it.isNotEmpty() }
    }

    fun shouldSkipEntry(name: String): Boolean {
        if (name.startsWith(".github/") || name.startsWith(".ide/")) return true
        if (name.startsWith(".git/") || name == ".gitignore" || name == ".cnb.yml") return true
        val base = name.substringAfterLast('/')
        if (base.equals("LICENSE", ignoreCase = true)) return true
        if (base.startsWith("README", ignoreCase = true)) return true
        if (base.endsWith(".webp") || base.endsWith(".png") || base.endsWith(".gif")) return true
        return false
    }
}
