/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.data.translation.OnlineTranslateTargets
import org.fcitx.fcitx5.android.data.translation.OnlineTranslator

/**
 * 翻译设置：偏好挂在同一 PreferenceCategory 内，避免多 Tab 下 dependency 跨屏崩溃。
 */
class TranslationSettingsFragment :
    ManagedPreferenceFragment(AppPrefs.getInstance().translation) {

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        addOnlineTranslateTargetsPref(screen, AppPrefs.getInstance().translation)
    }

    private fun addOnlineTranslateTargetsPref(
        screen: PreferenceScreen,
        tr: AppPrefs.Translation,
    ) {
        val ctx = preferenceManager.context
        val langs = OnlineTranslateTargets.allSelectable
        val pref = MultiSelectListPreference(ctx).apply {
            key = tr.onlineTranslateTargets.key
            title = getString(R.string.online_translate_targets)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isPersistent = false
            setEntries(langs.map { getString(it.nameRes) }.toTypedArray())
            setEntryValues(langs.map { it.code }.toTypedArray())
            val current = OnlineTranslateTargets.parse(tr.onlineTranslateTargets.getValue())
                .map { it.code }
                .toSet()
            values = current
            summary = getString(
                R.string.online_translate_targets_summary,
                OnlineTranslateTargets.summaryCodes(tr.onlineTranslateTargets.getValue()),
            )
            dependency = tr.onlineSentenceTranslate.key
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
                tr.onlineTranslateTargets.setValue(encoded)
                values = OnlineTranslateTargets.parse(encoded).map { it.code }.toSet()
                preference.summary = getString(
                    R.string.online_translate_targets_summary,
                    OnlineTranslateTargets.summaryCodes(encoded),
                )
                false
            }
        }
        val onlineKey = tr.onlineSentenceTranslate.key
        var host: PreferenceCategory? = null
        var afterOrder = 0
        for (i in 0 until screen.preferenceCount) {
            val p = screen.getPreference(i)
            if (p !is PreferenceCategory) continue
            for (j in 0 until p.preferenceCount) {
                val child = p.getPreference(j)
                if (child.key == onlineKey) {
                    host = p
                    afterOrder = child.order + 1
                    break
                }
            }
            if (host != null) break
        }
        pref.order = afterOrder
        (host ?: screen).addPreference(pref)
    }
}
