/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.prefs

/**
 * Pure matching for [HardwareKeyProfiles.detectSuggestedId]. Kept free of Android types so JVM
 * unit tests can cover the fingerprint rules without `R` / `InputDevice`.
 *
 * Matching is conservative: only known full-keyboard fingerprints (and keyboard+touchpad surface)
 * return a profile id. Ordinary touch phones return `null` so first-run can default to the
 * on-screen keyboard instead of seeding BlackBerry hardware bindings as if they were Q25.
 */
internal object HardwareKeyProfileDetect {

    fun suggestedId(
        manufacturer: String,
        brand: String,
        model: String,
        device: String,
        product: String,
        hasKeyboardTouchSurface: Boolean,
    ): String? {
        val blob = "$manufacturer $brand $model $device $product".lowercase()
        val isTitan = blob.contains("titan")
        val isElite = blob.contains("elite") ||
            blob.contains("titan2e") ||
            blob.contains("titan 2e")
        if (isElite) return "titan2_elite"
        if (hasKeyboardTouchSurface && isTitan) return "titan2_elite"
        // Titan Slim / Pocket / original Titan share the Titan 2 row (Fn / Sym), not Elite's
        // capacitive face or BlackBerry's 0/Alt_R.
        if (isTitan || blob.contains("titanslim") || blob.contains("titan slim") ||
            blob.contains("titanpocket") || blob.contains("titan pocket")
        ) {
            return "tt2"
        }
        if (
            blob.contains("q25") ||
            blob.contains("blackberry") ||
            blob.contains("keyone") ||
            blob.contains("key2") ||
            blob.contains("keyle") ||
            blob.contains("key2 le")
        ) {
            return "blackberry"
        }
        if (hasKeyboardTouchSurface) return "titan2_elite"
        return null
    }

    /** True when the fingerprint matches a known physical full-keyboard phone. */
    fun isKnownFullKeyboardDevice(
        manufacturer: String,
        brand: String,
        model: String,
        device: String,
        product: String,
        hasKeyboardTouchSurface: Boolean,
    ): Boolean = suggestedId(
        manufacturer, brand, model, device, product, hasKeyboardTouchSurface
    ) != null
}
