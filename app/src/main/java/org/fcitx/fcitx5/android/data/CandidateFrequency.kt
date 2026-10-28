/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data

import android.content.Context
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.FcitxApplication
import timber.log.Timber
import kotlin.math.ln

/**
 * Local candidate-pick memory: how often the user has selected each candidate string.
 *
 * The engine's [org.fcitx.fcitx5.android.core.CandidateWord] has no frequency field, so we count
 * commits that came from an armed candidate pick. Completely unfamiliar words stay at count 0
 * and keep the theme's original candidate color.
 */
object CandidateFrequency {

    private const val PREFS = "candidate_frequency"
    private const val KEY_COUNTS = "counts_v1"
    /** Cap stored entries so prefs stay small; drop the coldest when over. */
    private const val MAX_ENTRIES = 800

    /**
     * Soft saturation: count 1 ≈ light tint, ~20 ≈ near accent. Pure math — unit-testable.
     * @return blend factor in \[0, 1\]; 0 means keep [base] (unknown).
     */
    fun blendFactor(count: Int): Float {
        if (count <= 0) return 0f
        val t = ln(1.0 + count) / ln(1.0 + 20.0)
        return t.toFloat().coerceIn(0.28f, 0.88f)
    }

    fun colorFor(count: Int, base: Int, accent: Int): Int {
        val t = blendFactor(count)
        if (t <= 0f) return base
        return ColorUtils.blendARGB(base, accent, t)
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
