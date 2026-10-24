/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.pinyin.ContactsDictionary
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.data.theme.CandidateFont
import org.fcitx.fcitx5.android.data.translation.OnlineTranslateTargets
import org.fcitx.fcitx5.android.data.translation.OnlineTranslator
import org.fcitx.fcitx5.android.ui.main.MainViewModel

class CandidateBarSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().candidateBar) {

    private val viewModel: MainViewModel by activityViewModels()
    private var fontStatusPref: Preference? = null
    private var translateTargetsPref: MultiSelectListPreference? = null

    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            CandidateFont.import(requireContext(), uri)
            refreshFontStatus()
        }
    }

    private fun refreshFontStatus() {
        val pref = fontStatusPref ?: return
        pref.summary = when {
            CandidateFont.isImported(requireContext()) ->
                getString(R.string.candidate_font_status_imported)
            CandidateFont.isBundledReady(requireContext()) ->
                getString(R.string.candidate_font_status_bundled)
            else ->
                getString(R.string.candidate_font_status_missing)
        }
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val ctx = preferenceManager.context
        val bar = AppPrefs.getInstance().candidateBar
        addOnlineTranslateTargetsPref(screen, bar)
        val fontStatus = Preference(ctx).apply {
            key = "candidate_font_status"
            title = getString(R.string.candidate_font_status)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isSelectable = false
            isPersistent = false
        }
        fontStatusPref = fontStatus
        refreshFontStatus()
        screen.addPreference(fontStatus)
        screen.addPreference(Preference(ctx).apply {
            key = "candidate_font_import"
            title = getString(R.string.candidate_font_import)
            summary = getString(R.string.candidate_font_import_summary)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/font-sfnt", "*/*"))
                true
            }
        })
        screen.addPreference(Preference(ctx).apply {
            key = "candidate_font_clear"
            title = getString(R.string.candidate_font_clear)
            summary = getString(R.string.candidate_font_clear_summary)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                CandidateFont.clear(requireContext())
                refreshFontStatus()
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

    private fun addOnlineTranslateTargetsPref(
        screen: PreferenceScreen,
        bar: AppPrefs.CandidateBar,
    ) {
        val ctx = preferenceManager.context
        val langs = OnlineTranslateTargets.allSelectable
        val entryValues = langs.map { it.code }.toTypedArray()
        val entryLabels = langs.map { getString(it.nameRes) }.toTypedArray()
        val pref = MultiSelectListPreference(ctx).apply {
            key = bar.onlineTranslateTargets.key
            title = getString(R.string.online_translate_targets)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            // 存到我们自己的逗号串偏好，不走 Preference 默认的 StringSet。
            isPersistent = false
            setEntries(entryLabels)
            setEntryValues(entryValues)
            val current = OnlineTranslateTargets.parse(bar.onlineTranslateTargets.getValue())
                .map { it.code }
                .toSet()
            values = current
            summary = getString(
                R.string.online_translate_targets_summary,
                OnlineTranslateTargets.summaryCodes(bar.onlineTranslateTargets.getValue()),
            )
            dependency = bar.onlineSentenceTranslate.key
            setOnPreferenceChangeListener { preference, newValue ->
                @Suppress("UNCHECKED_CAST")
                val picked = (newValue as? Set<String>).orEmpty()
                val langsPicked = picked.mapNotNull { OnlineTranslator.Lang.fromCode(it) }
                    .distinct()
                    .take(OnlineTranslateTargets.MAX_SELECTED)
                val encoded = if (langsPicked.isEmpty()) {
                    OnlineTranslateTargets.DEFAULT_RAW
                } else {
                    OnlineTranslateTargets.encode(langsPicked)
                }
                bar.onlineTranslateTargets.setValue(encoded)
                values = OnlineTranslateTargets.parse(encoded).map { it.code }.toSet()
                preference.summary = getString(
                    R.string.online_translate_targets_summary,
                    OnlineTranslateTargets.summaryCodes(encoded),
                )
                false
            }
        }
        translateTargetsPref = pref
        // 插到「联网整句翻译」开关后面
        val onlineKey = bar.onlineSentenceTranslate.key
        var insertAfter = -1
        for (i in 0 until screen.preferenceCount) {
            if (screen.getPreference(i).key == onlineKey) {
                insertAfter = i
                break
            }
        }
        if (insertAfter >= 0) {
            // PreferenceScreen 没有 insert；先加再调整 order
            pref.order = screen.getPreference(insertAfter).order + 1
        }
        screen.addPreference(pref)
    }
}
