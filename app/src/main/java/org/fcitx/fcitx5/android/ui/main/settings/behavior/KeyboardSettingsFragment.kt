/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.EnglishInputMode
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

        val hw = AppPrefs.getInstance().hardwareKeyboard
        val englishCat = PreferenceCategory(requireContext()).apply {
            key = "virtual_keyboard_english_input"
            title = getString(R.string.english_input_category)
            isIconSpaceReserved = false
            order = -90
        }
        screen.addPreference(englishCat)

        val modes = EnglishInputMode.entries
        val modePref = ListPreference(requireContext()).apply {
            key = hw.englishInputMode.key
            title = getString(R.string.english_input_mode)
            summary = getString(R.string.english_input_mode_summary)
            setDefaultValue(hw.englishInputMode.defaultValue.name)
            entries = modes.map { getString(it.stringRes) }.toTypedArray()
            entryValues = modes.map { it.name }.toTypedArray()
            value = hw.englishInputMode.getValue().name
            isIconSpaceReserved = false
            isSingleLineTitle = false
            order = -89
        }
        val insertSpace = SwitchPreference(requireContext()).apply {
            key = hw.englishInsertSpace.key
            title = getString(R.string.hw_english_insert_space)
            summary = getString(R.string.hw_english_insert_space_summary)
            setDefaultValue(hw.englishInsertSpace.defaultValue)
            isChecked = hw.englishInsertSpace.getValue()
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isEnabled = hw.englishInputMode.getValue() == EnglishInputMode.WordHint
            order = -88
        }
        modePref.setOnPreferenceChangeListener { _, newValue ->
            insertSpace.isEnabled = newValue == EnglishInputMode.WordHint.name
            true
        }
        englishCat.addPreference(modePref)
        englishCat.addPreference(insertSpace)
    }
}
