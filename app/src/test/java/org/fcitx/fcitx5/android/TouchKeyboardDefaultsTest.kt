/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.data.prefs.HardwareKeyProfileDetect
import org.fcitx.fcitx5.android.data.prefs.TouchKeyboardDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchKeyboardDefaultsTest {

    @Test
    fun touchPhoneUsesDisabledCandidatesAndQwerty() {
        assertEquals("Disabled", TouchKeyboardDefaults.floatingCandidatesModeName(false))
        assertEquals("Qwerty", TouchKeyboardDefaults.virtualLayoutName())
    }

    @Test
    fun fullKeyboardUsesFollowInputDevice() {
        assertEquals("InputDevice", TouchKeyboardDefaults.floatingCandidatesModeName(true))
    }

    @Test
    fun jellyAndGenericPhonesAreNotFullKeyboard() {
        assertNull(
            HardwareKeyProfileDetect.suggestedId(
                manufacturer = "Unihertz",
                brand = "",
                model = "Jelly Max",
                device = "",
                product = "",
                hasKeyboardTouchSurface = false,
            )
        )
        assertFalse(
            HardwareKeyProfileDetect.isKnownFullKeyboardDevice(
                manufacturer = "Xiaomi",
                brand = "Redmi",
                model = "23013RK75C",
                device = "",
                product = "",
                hasKeyboardTouchSurface = false,
            )
        )
    }

    @Test
    fun q25AndEliteAreFullKeyboard() {
        assertTrue(
            HardwareKeyProfileDetect.isKnownFullKeyboardDevice(
                manufacturer = "BlackBerry",
                brand = "",
                model = "Q25",
                device = "",
                product = "",
                hasKeyboardTouchSurface = false,
            )
        )
        assertTrue(
            HardwareKeyProfileDetect.isKnownFullKeyboardDevice(
                manufacturer = "Unihertz",
                brand = "",
                model = "Titan 2 Elite",
                device = "TITAN2E",
                product = "",
                hasKeyboardTouchSurface = true,
            )
        )
    }
}
