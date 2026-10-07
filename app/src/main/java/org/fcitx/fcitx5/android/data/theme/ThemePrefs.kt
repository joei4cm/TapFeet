/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2023 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.data.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.edit
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceCategory
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

class ThemePrefs(sharedPreferences: SharedPreferences) :
    ManagedPreferenceCategory(R.string.theme, sharedPreferences) {

    private fun themePreference(
        @StringRes
        title: Int,
        key: String,
        defaultValue: Theme,
        @StringRes
        summary: Int? = null,
        enableUiOn: (() -> Boolean)? = null
    ): ManagedThemePreference {
        val pref = ManagedThemePreference(sharedPreferences, key, defaultValue)
        val ui = ManagedThemePreferenceUi(title, key, defaultValue, summary, enableUiOn)
        pref.register()
        ui.registerUi()
        return pref
    }

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
            5,
            R.string.landscape,
            "key_vertical_margin_landscape",
            4,
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
     * This is effectively an internal preference which does not need UI.
     */
    val normalModeTheme = ManagedThemePreference(
        sharedPreferences, "normal_mode_theme", ThemePreset.PixelDark
    ).also {
        it.register()
    }

    val followSystemDayNightTheme = switch(
        R.string.follow_system_day_night_theme,
        "follow_system_dark_mode",
        true,
        summary = R.string.follow_system_day_night_theme_summary
    )

    val lightModeTheme = themePreference(
        R.string.light_mode_theme,
        "light_mode_theme",
        ThemePreset.PixelLight,
        enableUiOn = {
            followSystemDayNightTheme.getValue()
        })

    val darkModeTheme = themePreference(
        R.string.dark_mode_theme,
        "dark_mode_theme",
        ThemePreset.PixelDark,
        enableUiOn = {
            followSystemDayNightTheme.getValue()
        })

    val dayNightModePrefNames = setOf(
        followSystemDayNightTheme.key,
        lightModeTheme.key,
        darkModeTheme.key
    )

    init {
        migrateGboardLikeDefaults()
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
            // Day/night still on WeChat stock names → Pixel (Gboard-like blue accent)
            val light = sharedPreferences.getString("light_mode_theme", null)
            if (light == null || light == "WeChatLight") {
                putString("light_mode_theme", ThemePreset.PixelLight.name)
            }
            val dark = sharedPreferences.getString("dark_mode_theme", null)
            if (dark == null || dark == "WeChatDark") {
                putString("dark_mode_theme", ThemePreset.PixelDark.name)
            }
            val normal = sharedPreferences.getString("normal_mode_theme", null)
            if (normal == null || normal == "WeChatDark" || normal == "WeChatLight") {
                putString("normal_mode_theme", ThemePreset.PixelDark.name)
            }
            putBoolean(flag, true)
        }
    }
}
