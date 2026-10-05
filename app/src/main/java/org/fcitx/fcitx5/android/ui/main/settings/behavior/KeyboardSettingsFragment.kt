/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.data.voice.VoiceModelManager
import org.fcitx.fcitx5.android.utils.toast

class KeyboardSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().keyboard) {

    private var voiceModelPref: Preference? = null

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val ctx = preferenceManager.context
        val pref = Preference(ctx).apply {
            key = "voice_model_status"
            title = getString(R.string.voice_model_status)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isPersistent = false
            order = -1
            setOnPreferenceClickListener {
                when (VoiceModelManager.state.value) {
                    VoiceModelManager.State.Ready ->
                        requireContext().toast(R.string.voice_model_status_ready)
                    is VoiceModelManager.State.Downloading ->
                        requireContext().toast(R.string.voice_input_model_downloading)
                    else -> {
                        requireContext().toast(R.string.voice_input_model_download_start)
                        VoiceModelManager.ensureDownloaded(
                            onSuccess = {
                                requireContext().toast(R.string.voice_input_model_download_done)
                            },
                            onFailure = {
                                requireContext().toast(R.string.voice_input_model_download_error)
                            },
                        )
                    }
                }
                true
            }
        }
        voiceModelPref = pref
        val parent = screen.findPreference<Preference>("built_in_voice_input")?.parent ?: screen
        parent.addPreference(pref)
        bindVoiceModelSummary(VoiceModelManager.state.value)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                VoiceModelManager.state.collect { bindVoiceModelSummary(it) }
            }
        }
    }

    private fun bindVoiceModelSummary(state: VoiceModelManager.State) {
        voiceModelPref?.summary = when (state) {
            VoiceModelManager.State.NotDownloaded ->
                getString(R.string.voice_model_status_missing)
            is VoiceModelManager.State.Downloading ->
                if (state.totalBytes > 0) {
                    val pct = (state.downloadedBytes * 100 / state.totalBytes).toInt().coerceIn(0, 100)
                    getString(R.string.voice_model_status_downloading, pct)
                } else {
                    getString(
                        R.string.voice_model_status_downloading_bytes,
                        state.downloadedBytes / (1024L * 1024L),
                    )
                }
            VoiceModelManager.State.Ready ->
                getString(R.string.voice_model_status_ready)
            is VoiceModelManager.State.Error ->
                getString(R.string.voice_model_status_error, state.message)
        }
    }
}
