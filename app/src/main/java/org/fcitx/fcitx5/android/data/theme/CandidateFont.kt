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
import java.io.IOException
import timber.log.Timber

/**
 * Typefaces for candidate text and IME chrome (keys, preedit, popups).
 *
 * Default is bundled Noto Sans SC (assets), not the system `sans-serif` family: Elite and similar
 * ROMs often ship a Latin-first default with incomplete CJK coverage, which becomes tofu.
 * An imported TTF/OTF still wins for **candidates**; on API 29+ the bundled face is chained as
 * a custom fallback so a Latin-only import does not hide 汉字. Keys always use the bundled face.
 */
object CandidateFont {

    private const val FILE = "candidate_font.ttf"
    private const val BUNDLED_FILE = "NotoSansSC-Regular.otf"
    private const val MIN_BUNDLED_BYTES = 1_000_000L
    private const val FAMILY = "sans-serif"
    const val ASSET = "fonts/NotoSansSC-Regular.otf"

    @Volatile
    private var cached: Typeface? = null
    private var cachedMod = 0L
    private var cachedLen = -1L

    @Volatile
    private var bundledNormal: Typeface? = null

    @Volatile
    private var bundledBold: Typeface? = null

    @Volatile
    private var bundledFamily: Any? = null

    fun file(context: Context): File = File(context.filesDir, FILE)

    fun isImported(context: Context): Boolean {
        val f = file(context)
        return f.isFile && f.length() > 0
    }

    fun isBundledReady(context: Context): Boolean = try {
        bundledFile(context).length() > MIN_BUNDLED_BYTES
    } catch (_: Exception) {
        false
    }

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
            return importedTypeface(context, f, style)
        }
        return uiTypeface(context, style)
    }

    /** Bundled Noto Sans SC, never the user import. Safe to pass as [android.widget.TextView.setTypeface]. */
    fun uiTypeface(context: Context, style: Int = Typeface.NORMAL): Typeface {
        val bold = style == Typeface.BOLD
        val hit = if (bold) bundledBold else bundledNormal
        if (hit != null) return hit
        val created = buildBundled(context, if (bold) Typeface.BOLD else Typeface.NORMAL)
        if (bold) bundledBold = created else bundledNormal = created
        return created
    }

    private fun importedTypeface(context: Context, f: File, style: Int): Typeface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val font = Font.Builder(f).apply {
                if (style == Typeface.BOLD) setWeight(700)
            }.build()
            val family = FontFamily.Builder(font).build()
            val builder = Typeface.CustomFallbackBuilder(family)
            bundledFontFamily(context)?.let { builder.addCustomFallback(it) }
            return builder.setSystemFallback(FAMILY).build()
        }
        val base = cachedFileTypeface(f)
        return Typeface.create(base, style)
    }

    private fun bundledFile(context: Context): File {
        val dest = File(context.filesDir, BUNDLED_FILE)
        if (dest.isFile && dest.length() > MIN_BUNDLED_BYTES) return dest
        val part = File(context.filesDir, "$BUNDLED_FILE.part")
        try {
            context.assets.open(ASSET).use { input ->
                part.outputStream().use { input.copyTo(it) }
            }
            if (part.length() <= MIN_BUNDLED_BYTES) {
                val size = part.length()
                part.delete()
                throw IOException("extracted CJK font too small: $size")
            }
            dest.delete()
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        }
        return dest
    }

    private fun buildBundled(context: Context, style: Int): Typeface {
        return try {
            val file = bundledFile(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val font = Font.Builder(file).apply {
                    if (style == Typeface.BOLD) setWeight(700)
                }.build()
                Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                    .setSystemFallback(FAMILY)
                    .build()
            } else {
                val base = Typeface.createFromFile(file)
                if (style == Typeface.NORMAL) base else Typeface.create(base, style)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bundled CJK font")
            Typeface.create(FAMILY, style)
        }
    }

    private fun bundledFontFamily(context: Context): FontFamily? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        (bundledFamily as? FontFamily)?.let { return it }
        return try {
            FontFamily.Builder(Font.Builder(bundledFile(context)).build()).build().also {
                bundledFamily = it
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bundled CJK font family")
            null
        }
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
