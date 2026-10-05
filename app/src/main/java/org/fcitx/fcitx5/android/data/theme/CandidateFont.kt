/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * User-imported TTF/OTF for candidate text. One file in app storage; weight is a Typeface
 * style (normal / bold), tracking is [android.widget.TextView.setLetterSpacing].
 *
 * The default face is the `sans-serif` **family**, not [Typeface.DEFAULT] wrapped with
 * [Typeface.create]: the latter drops Android's CJK fallback chain and Han glyphs become tofu.
 * An imported file on API 29+ also chains `sans-serif` so a Latin-only TTF still renders 汉字.
 */
object CandidateFont {

    private const val FILE = "candidate_font.ttf"
    private const val FAMILY = "sans-serif"

    @Volatile
    private var cached: Typeface? = null
    private var cachedMod = 0L
    private var cachedLen = -1L

    fun file(context: Context): File = File(context.filesDir, FILE)

    fun import(context: Context, uri: Uri): Boolean {
        val dest = file(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: return false
        invalidate()
        return dest.length() > 0
    }

    fun clear(context: Context) {
        file(context).delete()
        invalidate()
    }

    fun typeface(context: Context, bold: Boolean): Typeface {
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        val f = file(context)
        if (f.isFile && f.length() > 0) {
            return importedTypeface(f, style)
        }
        return Typeface.create(FAMILY, style)
    }

    private fun importedTypeface(f: File, style: Int): Typeface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val font = Font.Builder(f).apply {
                if (style == Typeface.BOLD) setWeight(700)
            }.build()
            val family = FontFamily.Builder(font).build()
            return Typeface.CustomFallbackBuilder(family)
                .setSystemFallback(FAMILY)
                .build()
        }
        val base = cachedFileTypeface(f)
        return Typeface.create(base, style)
    }

    private fun cachedFileTypeface(f: File): Typeface {
        val mod = f.lastModified()
        val len = f.length()
        val hit = cached
        if (hit != null && cachedMod == mod && cachedLen == len) {
            return hit
        }
        return Typeface.createFromFile(f).also {
            cached = it
            cachedMod = mod
            cachedLen = len
        }
    }

    private fun invalidate() {
        cached = null
        cachedMod = 0L
        cachedLen = -1L
    }
}
