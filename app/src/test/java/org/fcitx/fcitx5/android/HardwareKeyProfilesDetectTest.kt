/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.data.prefs.HardwareKeyProfileDetect
import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareKeyProfilesDetectTest {

    private fun detect(
        manufacturer: String = "",
        brand: String = "",
        model: String = "",
        device: String = "",
        product: String = "",
        hasKeyboardTouchSurface: Boolean = false,
    ) = HardwareKeyProfileDetect.suggestedId(
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
    fun unknownPhoneStaysBlackberry() {
        assertEquals(
            "blackberry",
            detect(manufacturer = "Unihertz", model = "Jelly Max")
        )
        assertEquals("blackberry", detect())
    }

    @Test
    fun touchSurfaceAloneIsElite() {
        assertEquals(
            "titan2_elite",
            detect(hasKeyboardTouchSurface = true)
        )
    }
}
