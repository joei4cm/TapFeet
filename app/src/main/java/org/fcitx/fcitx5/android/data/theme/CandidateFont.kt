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
 * Default chain (API 29+):
 *  1. imported TTF/OTF (candidates only, optional)
 *  2. bundled Noto Sans SC — common 简体
 *  3. bundled Plangothic Ext — CJK Extension B–I rare glyphs (e.g. 𫚥)
 *  4. system `sans-serif`
 *
 * Elite and similar ROMs often ship a Latin-first default with incomplete CJK; Noto alone is still
 * a SC subset and misses Extension C forms such as U+2B6A5, so Plangothic fills those holes.
 */
object CandidateFont {

    private const val FILE = "candidate_font.ttf"
    private const val BUNDLED_FILE = "NotoSansSC-Regular.otf"
    private const val EXT_FILE = "PlangothicExt-Regular.ttf"
    private const val MIN_BUNDLED_BYTES = 1_000_000L
    private const val MIN_EXT_BYTES = 1_000_000L
    private const val FAMILY = "sans-serif"
    const val ASSET = "fonts/NotoSansSC-Regular.otf"
    const val EXT_ASSET = "fonts/PlangothicExt-Regular.ttf"

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

    @Volatile
    private var extFamily: Any? = null

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

    /** Bundled Noto Sans SC (+ Ext fallback on API 29+). Safe for [android.widget.TextView.setTypeface]. */
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
            extFontFamily(context)?.let { builder.addCustomFallback(it) }
            return builder.setSystemFallback(FAMILY).build()
        }
        val base = cachedFileTypeface(f)
        return Typeface.create(base, style)
    }

    private fun extractAsset(context: Context, assetPath: String, destName: String, minBytes: Long): File {
        val dest = File(context.filesDir, destName)
        if (dest.isFile && dest.length() > minBytes) return dest
        val part = File(context.filesDir, "$destName.part")
        try {
            context.assets.open(assetPath).use { input ->
                part.outputStream().use { input.copyTo(it) }
            }
            if (part.length() <= minBytes) {
                val size = part.length()
                part.delete()
                throw IOException("extracted font too small: $destName ($size)")
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

    private fun bundledFile(context: Context): File =
        extractAsset(context, ASSET, BUNDLED_FILE, MIN_BUNDLED_BYTES)

    private fun extFile(context: Context): File =
        extractAsset(context, EXT_ASSET, EXT_FILE, MIN_EXT_BYTES)

    private fun buildBundled(context: Context, style: Int): Typeface {
        return try {
            val file = bundledFile(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val font = Font.Builder(file).apply {
                    if (style == Typeface.BOLD) setWeight(700)
                }.build()
                val builder = Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                extFontFamily(context)?.let { builder.addCustomFallback(it) }
                builder.setSystemFallback(FAMILY).build()
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

    private fun extFontFamily(context: Context): FontFamily? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        (extFamily as? FontFamily)?.let { return it }
        return try {
            FontFamily.Builder(Font.Builder(extFile(context)).build()).build().also {
                extFamily = it
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load Plangothic Ext font family")
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
        bundledNormal = null
        bundledBold = null
        bundledFamily = null
        extFamily = null
    }
}
