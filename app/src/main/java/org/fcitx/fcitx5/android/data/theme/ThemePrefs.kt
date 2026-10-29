/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2023 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.data.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceCategory
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

class ThemePrefs(sharedPreferences: SharedPreferences) :
    ManagedPreferenceCategory(R.string.theme, sharedPreferences) {

    val keyBorder = switch(R.string.key_border, "key_border", true)

    // Gboard-like soft keys: filled pills with light shadow, not hard 1dp strokes.
    val keyBorderStroke = switch(
        R.string.key_border_stroke, "key_border_stroke", false,
        enableUiOn = { keyBorder.getValue() }
    )

    val keyRippleEffect = switch(R.string.key_ripple_effect, "key_ripple_effect", true)

    val keyHorizontalMargin: ManagedPreference.PInt
    val keyHorizontalMarginLandscape: ManagedPreference.PInt

    init {
        val (primary, secondary) = twinInt(
            R.string.key_horizontal_margin,
            R.string.portrait,
            "key_horizontal_margin",
            2,
            R.string.landscape,
            "key_horizontal_margin_landscape",
            2,
            0,
            24,
            "dp"
        )
        keyHorizontalMargin = primary
        keyHorizontalMarginLandscape = secondary
    }

    val keyVerticalMargin: ManagedPreference.PInt
    val keyVerticalMarginLandscape: ManagedPreference.PInt

    init {
        val (primary, secondary) = twinInt(
            R.string.key_vertical_margin,
            R.string.portrait,
            "key_vertical_margin",
            3,
            R.string.landscape,
            "key_vertical_margin_landscape",
            3,
            0,
            24,
            "dp"
        )
        keyVerticalMargin = primary
        keyVerticalMarginLandscape = secondary
    }

    val keyRadius = int(R.string.key_radius, "key_radius", 10, 0, 48, "dp")

    val textEditingButtonRadius =
        int(R.string.text_editing_button_radius, "text_editing_button_radius", 8, 0, 48, "dp")

    val clipboardEntryRadius =
        int(R.string.clipboard_entry_radius, "clipboard_entry_radius", 2, 0, 48, "dp")

    enum class PunctuationPosition(override val stringRes: Int) : ManagedPreferenceEnum {
        None(R.string.punctuation_pos_none),
        Bottom(R.string.punctuation_pos_bottom),
        TopRight(R.string.punctuation_pos_top_right);
    }

    val punctuationPosition = enumList(
        R.string.punctuation_position,
        "punctuation_position",
        PunctuationPosition.Bottom
    )

    enum class NavbarBackground(override val stringRes: Int) : ManagedPreferenceEnum {
        None(R.string.navbar_bkg_none),
        ColorOnly(R.string.navbar_bkg_color_only),
        Full(R.string.navbar_bkg_full);
    }

    val navbarBackground = enumList(
        R.string.navbar_background,
        "navbar_background",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) NavbarBackground.Full else NavbarBackground.ColorOnly,
        // 35+ forces edge to edge
        enableUiOn = { Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM }
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            sharedPreferences.edit {
                remove(this@apply.key)
            }
        }
    }

    /**
     * When [followSystemDayNightTheme] is disabled, this theme is used.
     * Internal — no Preference UI; pick from the theme grid instead.
     */
    val normalModeTheme = ManagedThemePreference(
        sharedPreferences, "normal_mode_theme", ThemeMonet.getDark()
    ).also {
        it.register()
    }

    /**
     * Gboard-like: follow system day/night and use Material You (Monet) by default.
     * Toggle lives on the theme list (not the Configure tab).
     */
    val followSystemDayNightTheme = bool("follow_system_dark_mode", true)

    /** Day theme while [followSystemDayNightTheme] is on. Internal — set via theme grid. */
    val lightModeTheme = ManagedThemePreference(
        sharedPreferences, "light_mode_theme", ThemeMonet.getLight()
    ).also {
        it.register()
    }

    /** Night theme while [followSystemDayNightTheme] is on. Internal — set via theme grid. */
    val darkModeTheme = ManagedThemePreference(
        sharedPreferences, "dark_mode_theme", ThemeMonet.getDark()
    ).also {
        it.register()
    }

    val dayNightModePrefNames = setOf(
        followSystemDayNightTheme.key,
        lightModeTheme.key,
        darkModeTheme.key
    )

    init {
        migrateGboardLikeDefaults()
        migrateSystemMonetDefaults()
    }

    /**
     * 老安装仍停在描边+4dp 圆角时，一次性迁到接近 Gboard 的软键默认。
     * 用户若已改过半径/描边，则不动。
     */
    private fun migrateGboardLikeDefaults() {
        val flag = "theme_gboard_like_defaults_v1"
        if (sharedPreferences.getBoolean(flag, false)) return
        sharedPreferences.edit {
            val radius = sharedPreferences.getInt("key_radius", 4)
            if (radius == 4) putInt("key_radius", 10)
            if (sharedPreferences.contains("key_border_stroke") &&
                sharedPreferences.getBoolean("key_border_stroke", true)
            ) {
                // only clear stroke when it was never customized away from old default true
                // and radius was still stock — handled above; force stroke off when radius migrated
                if (radius == 4) putBoolean("key_border_stroke", false)
            } else if (!sharedPreferences.contains("key_border_stroke")) {
                putBoolean("key_border_stroke", false)
            }
            val h = sharedPreferences.getInt("key_horizontal_margin", 3)
            if (h == 3) putInt("key_horizontal_margin", 2)
            val v = sharedPreferences.getInt("key_vertical_margin", 7)
            if (v == 7) putInt("key_vertical_margin", 5)
            putBoolean(flag, true)
        }
    }

    /**
     * Gboard-like system colors: day/night slots that still point at WeChat/Pixel stock
     * themes move to Monet (Material You). Follow-system stays on unless the user already
     * turned it off.
     */
    private fun migrateSystemMonetDefaults() {
        val flag = "theme_system_monet_defaults_v1"
        if (sharedPreferences.getBoolean(flag, false)) return
        val stockLight = setOf("WeChatLight", "PixelLight", "MaterialLight")
        val stockDark = setOf("WeChatDark", "PixelDark", "MaterialDark")
        sharedPreferences.edit {
            val light = sharedPreferences.getString("light_mode_theme", null)
            if (light == null || light in stockLight) {
                putString("light_mode_theme", "MonetLight")
            }
            val dark = sharedPreferences.getString("dark_mode_theme", null)
            if (dark == null || dark in stockDark) {
                putString("dark_mode_theme", "MonetDark")
            }
            val normal = sharedPreferences.getString("normal_mode_theme", null)
            if (normal == null || normal in stockLight || normal in stockDark) {
                putString("normal_mode_theme", "MonetDark")
            }
            if (!sharedPreferences.contains("follow_system_dark_mode")) {
                putBoolean("follow_system_dark_mode", true)
            }
            putBoolean(flag, true)
        }
    }
}
