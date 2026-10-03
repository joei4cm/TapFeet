/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.pinyin.ContactsDictionary
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.data.theme.CandidateFont
import org.fcitx.fcitx5.android.ui.main.MainViewModel

class CandidateBarSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().candidateBar) {

    private val viewModel: MainViewModel by activityViewModels()

    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) CandidateFont.import(requireContext(), uri)
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val ctx = preferenceManager.context
        val bar = AppPrefs.getInstance().candidateBar
        screen.addPreference(Preference(ctx).apply {
            key = "candidate_font_import"
            title = getString(R.string.candidate_font_import)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/font-sfnt", "*/*"))
                true
            }
        })
        screen.addPreference(Preference(ctx).apply {
            key = "candidate_font_clear"
            title = getString(R.string.candidate_font_clear)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                CandidateFont.clear(requireContext())
                true
            }
        })
        screen.findPreference<SwitchPreference>(bar.contactsDictionary.key)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                if (requireContext().checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    org.fcitx.fcitx5.android.utils.AppUtil.launchMainToContactsPermission(requireContext())
                } else {
                    ContactsDictionary.maybeSync(requireContext(), viewModel.fcitx, force = true)
                }
            } else {
                ContactsDictionary.clear(requireContext(), viewModel.fcitx)
            }
            true
        }
    }
}
