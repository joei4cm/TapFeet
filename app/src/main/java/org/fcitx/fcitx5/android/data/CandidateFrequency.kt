/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.FcitxApplication
import timber.log.Timber

/**
 * Local candidate-pick memory: how often the user has selected each candidate string.
 *
 * The engine's [org.fcitx.fcitx5.android.core.CandidateWord] has no frequency field, so we count
 * commits that came from an armed candidate pick. Completely unfamiliar words stay at count 0
 * and keep the theme's original candidate color.
 *
 * Known counts map to discrete [Grade] steps so the tint reads as a ladder (soft → peak), not a
 * binary “top vs rest”. Call sites pass the theme accent (e.g. [Theme.genericActiveBackgroundColor]).
 * A light accent is darkened before blending so dark text on a white bar never washes out to white.
 */
object CandidateFrequency {

    private const val PREFS = "candidate_frequency"
    private const val KEY_COUNTS = "counts_v1"
    /** Cap stored entries so prefs stay small; drop the coldest when over. */
    private const val MAX_ENTRIES = 800

    /** Discrete frequency bands for candidate tint. */
    enum class Grade {
        /** Never picked — keep theme base color. */
        None,
        Soft,
        Medium,
        Strong,
        Peak,
    }

    /**
     * Map a raw pick count to a grade. Thresholds are intentionally coarse so nearby counts share
     * a band and the ladder stays readable.
     */
    fun grade(count: Int): Grade = when {
        count <= 0 -> Grade.None
        count < 3 -> Grade.Soft
        count < 8 -> Grade.Medium
        count < 20 -> Grade.Strong
        else -> Grade.Peak
    }

    /**
     * Blend strength for [grade]. Pure math — unit-testable.
     * @return factor in \[0, 1\]; 0 means keep [base] (unknown).
     */
    fun blendFactor(count: Int): Float = when (grade(count)) {
        Grade.None -> 0f
        Grade.Soft -> 0.28f
        Grade.Medium -> 0.48f
        Grade.Strong -> 0.68f
        Grade.Peak -> 0.88f
    }

    fun colorFor(count: Int, base: Int, accent: Int): Int {
        val t = blendFactor(count)
        if (t <= 0f) return base
        return blendArgb(base, readableAccent(base, accent), t)
    }

    /**
     * Accent used as the blend target. When the accent is lighter than [base] (typical of a pale
     * or white highlight on a light theme), blending would fade dark glyphs toward white. Darken
     * the accent until it is no lighter than the base text.
     */
    fun readableAccent(base: Int, accent: Int): Int {
        val ceiling = luminance(base) + 0.04
        var color = accent
        var guard = 0
        while (luminance(color) > ceiling && guard < 8) {
            color = blendArgb(color, 0xFF000000.toInt(), 0.45f)
            guard++
        }
        return color
    }

    /** Relative luminance in \[0, 1\]. Pure math — unit-testable. */
    fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val s = ((color ushr shift) and 0xFF) / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Pure ARGB lerp — no Android Color APIs so JVM unit tests can exercise it. */
    fun blendArgb(from: Int, to: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        val inv = 1f - t
        fun ch(shift: Int): Int {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            return (a * inv + b * t).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    @Volatile
    private var cache: MutableMap<String, Int>? = null

    private fun prefs() = FcitxApplication.getInstance().directBootAwareContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(): MutableMap<String, Int> {
        cache?.let { return it }
        val raw = prefs().getString(KEY_COUNTS, null)
        val map = mutableMapOf<String, Int>()
        if (!raw.isNullOrEmpty()) {
            try {
                Json.decodeFromString<Map<String, Int>>(raw).forEach { (k, v) ->
                    if (k.isNotBlank() && v > 0) map[k] = v
                }
            } catch (e: Exception) {
                Timber.w(e, "CandidateFrequency: corrupt prefs, resetting")
            }
        }
        cache = map
        return map
    }

    private fun save(map: Map<String, Int>) {
        prefs().edit {
            putString(KEY_COUNTS, Json.encodeToString(map))
        }
    }

    fun countOf(text: String): Int {
        if (text.isBlank()) return 0
        return load()[text] ?: 0
    }

    fun record(text: String) {
        if (text.isBlank()) return
        val map = load()
        map[text] = (map[text] ?: 0) + 1
        if (map.size > MAX_ENTRIES) {
            // Drop the coldest half of the overflow (lowest counts first).
            val overflow = map.size - MAX_ENTRIES
            map.entries
                .sortedBy { it.value }
                .take(overflow)
                .forEach { map.remove(it.key) }
        }
        save(map)
    }
}
