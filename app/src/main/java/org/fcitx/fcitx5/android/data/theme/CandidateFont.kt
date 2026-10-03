/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

/**
 * User-imported TTF/OTF for candidate text. One file in app storage; weight is a Typeface
 * style (normal / bold), tracking is [android.widget.TextView.setLetterSpacing].
 */
object CandidateFont {

    private const val FILE = "candidate_font.ttf"

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
        val f = file(context)
        val base = if (f.isFile && f.length() > 0) {
            val mod = f.lastModified()
            val len = f.length()
            val hit = cached
            if (hit != null && cachedMod == mod && cachedLen == len) {
                hit
            } else {
                Typeface.createFromFile(f).also {
                    cached = it
                    cachedMod = mod
                    cachedLen = len
                }
            }
        } else {
            Typeface.DEFAULT
        }
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun invalidate() {
        cached = null
        cachedMod = 0L
        cachedLen = -1L
    }
}
