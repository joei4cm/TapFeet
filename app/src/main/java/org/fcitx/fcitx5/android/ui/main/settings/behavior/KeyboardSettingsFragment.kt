/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim

/** 虚拟键盘设置。语音相关项已迁到 [VoiceSettingsFragment]。 */
class KeyboardSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().keyboard) {

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        // 高度在「尺寸」分类里；主题单独成页，这里加入口方便从虚拟键盘设置跳转。
        val theme = Preference(requireContext()).apply {
            key = "virtual_keyboard_theme_link"
            title = getString(R.string.theme)
            summary = getString(R.string.virtual_keyboard_theme_summary)
            isIconSpaceReserved = false
            order = -100
            setOnPreferenceClickListener {
                navigateWithAnim(SettingsRoute.Theme)
                true
            }
        }
        screen.addPreference(theme)
    }
}
