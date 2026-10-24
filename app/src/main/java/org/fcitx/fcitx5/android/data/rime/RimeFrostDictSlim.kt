/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

/**
 * 白霜源包全量细胞词库过重；安装时裁成手机可用的核心表，保留白霜词频。
 * 纯字符串处理，便于单测。
 */
object RimeFrostDictSlim {

    private val IMPORT_TABLES_REGEX = Regex(
        """import_tables:\n(?:[ \t]*-[^\n]*\n|[ \t]*#[^\n]*\n|\n)+""",
        RegexOption.MULTILINE,
    )

    private val SLIM_IMPORTS = """
        import_tables:
          - cn_dicts/8105
          - cn_dicts/base
          - cn_dicts/ext
          - cn_dicts/others
          - cn_dicts/corrections
    """.trimIndent()

    /**
     * @return 裁剪后的全文；若找不到 `import_tables` 块则原样返回。
     */
    fun apply(original: String): String {
        val replaced = IMPORT_TABLES_REGEX.replace(original, SLIM_IMPORTS + "\n\n")
        return replaced
    }

    fun changed(original: String, slimmed: String): Boolean = original != slimmed
}
