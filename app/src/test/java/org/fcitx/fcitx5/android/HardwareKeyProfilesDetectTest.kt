/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.data.prefs.HardwareKeyProfileDetect
import org.fcitx.fcitx5.android.data.prefs.HardwareKeyProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareKeyProfilesDetectTest {

    private fun detect(
        manufacturer: String = "",
        brand: String = "",
        model: String = "",
        device: String = "",
        product: String = "",
        hasKeyboardTouchSurface: Boolean = false,
    ): String? = HardwareKeyProfileDetect.suggestedId(
        manufacturer, brand, model, device, product, hasKeyboardTouchSurface
    )

    private fun suggested(
        manufacturer: String = "",
        brand: String = "",
        model: String = "",
        device: String = "",
        product: String = "",
        hasKeyboardTouchSurface: Boolean = false,
    ) = HardwareKeyProfiles.detectSuggestedId(
        manufacturer, brand, model, device, product, hasKeyboardTouchSurface
    )

    @Test
    fun titan2EliteModel() {
        assertEquals(
            "titan2_elite",
            detect(manufacturer = "Unihertz", model = "Titan 2 Elite", device = "TITAN2E")
        )
        assertEquals(
            "titan2_elite",
            detect(product = "titan2e")
        )
    }

    @Test
    fun titan2WithoutElite() {
        assertEquals(
            "tt2",
            detect(manufacturer = "Unihertz", model = "Titan 2", device = "Titan2")
        )
    }

    @Test
    fun titanTouchSurfaceIsElite() {
        assertEquals(
            "titan2_elite",
            detect(manufacturer = "Unihertz", model = "Titan2", hasKeyboardTouchSurface = true)
        )
    }

    @Test
    fun q25AndBlackberry() {
        assertEquals("blackberry", detect(model = "Q25"))
        assertEquals("blackberry", detect(manufacturer = "BlackBerry", model = "KEYone"))
        assertEquals("blackberry", detect(model = "KEY2 LE"))
        assertEquals("blackberry", detect(model = "Key2LE"))
    }

    @Test
    fun titanSlimAndPocketUseTt2() {
        assertEquals("tt2", detect(manufacturer = "Unihertz", model = "Titan Slim"))
        assertEquals("tt2", detect(model = "Titan Pocket"))
    }

    @Test
    fun unknownPhoneHasNoFullKeyboardProfile() {
        // Touch / unknown phones must not pretend to be Q25; profile seeding falls back elsewhere.
        assertNull(detect(manufacturer = "Unihertz", model = "Jelly Max"))
        assertNull(detect())
    }

    @Test
    fun unknownPhoneSeedsTouchProfileNotBlackberry() {
        assertEquals("touch", suggested(manufacturer = "Unihertz", model = "Jelly Max"))
        assertEquals("touch", suggested(manufacturer = "Xiaomi", model = "23013RK75C"))
        assertEquals("touch", suggested())
        assertFalse(HardwareKeyProfiles.isFullKeyboardProfile(HardwareKeyProfiles.TOUCH))
        assertTrue(HardwareKeyProfiles.isFullKeyboardProfile(HardwareKeyProfiles.BLACKBERRY))
    }

    @Test
    fun bareTouchSurfaceIsNotElite() {
        // Xiaomi and other phones often enumerate KEYBOARD|TOUCHPAD; must stay touch.
        assertNull(detect(hasKeyboardTouchSurface = true))
        assertNull(
            detect(
                manufacturer = "Xiaomi",
                model = "23013RK75C",
                hasKeyboardTouchSurface = true,
            )
        )
        assertEquals("touch", suggested(manufacturer = "Xiaomi", hasKeyboardTouchSurface = true))
    }

    @Test
    fun unihertzJellyWithTouchSurfaceStaysTouch() {
        assertNull(
            detect(manufacturer = "Unihertz", model = "Jelly Max", hasKeyboardTouchSurface = true),
        )
        assertEquals(
            "touch",
            suggested(manufacturer = "Unihertz", model = "Jelly Max", hasKeyboardTouchSurface = true),
        )
    }
}
