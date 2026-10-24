/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.prefs

/**
 * First-run defaults for the on-screen keyboard path.
 *
 * Pure helpers so JVM tests can assert the policy without SharedPreferences / DeviceInfo.
 */
object TouchKeyboardDefaults {

    /**
     * @param knownFullKeyboard true for Q25 / Titan / Elite fingerprints (and keyboard touch surface).
     * @return preference name for [org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesMode]:
     *   `InputDevice` on full-keyboard phones, `Disabled` (always show soft keyboard) otherwise.
     */
    fun floatingCandidatesModeName(knownFullKeyboard: Boolean): String =
        if (knownFullKeyboard) "InputDevice" else "Disabled"

    /** Soft-keyboard layout name preference default: always QWERTY; user can switch to 九键 in settings. */
    fun virtualLayoutName(): String = "Qwerty"
}
