/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.data.voice.VoiceAsrKind
import org.fcitx.fcitx5.android.data.voice.VoiceModelManager
import org.fcitx.fcitx5.android.utils.setup
import org.fcitx.fcitx5.android.utils.toast

class VoiceSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().voice) {

    private var statusPref: Preference? = null
    private var downloadPref: Preference? = null
    private var deletePref: Preference? = null

    @Keep
    private val asrKindListener = ManagedPreference.OnChangeListener<VoiceAsrKind> { _, _ ->
        updateUi(VoiceModelManager.state.value)
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val ctx = screen.context
        val cat = PreferenceCategory(ctx).apply {
            setTitle(R.string.voice_model_management)
            isIconSpaceReserved = false
            // 排在引擎相关偏好之后
            order = 10_000
        }
        screen.addPreference(cat)

        statusPref = Preference(ctx).apply {
            setup(ctx.getString(R.string.voice_model_status))
            isSelectable = false
            order = 10_010
        }
        cat.addPreference(statusPref!!)

        downloadPref = Preference(ctx).apply {
            setup(
                ctx.getString(R.string.voice_model_download),
                icon = R.drawable.ic_baseline_mic_24,
            ) { onDownloadClicked() }
            order = 10_020
        }
        cat.addPreference(downloadPref!!)

        deletePref = Preference(ctx).apply {
            setup(
                ctx.getString(R.string.voice_model_delete),
                icon = R.drawable.ic_baseline_delete_24,
            ) { onDeleteClicked() }
            order = 10_030
        }
        cat.addPreference(deletePref!!)

        updateUi(VoiceModelManager.state.value)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AppPrefs.getInstance().voice.voiceAsrKind.registerOnChangeListener(asrKindListener)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                VoiceModelManager.state.collect { updateUi(it) }
            }
        }
    }

    override fun onDestroyView() {
        AppPrefs.getInstance().voice.voiceAsrKind.unregisterOnChangeListener(asrKindListener)
        super.onDestroyView()
    }

    private fun updateUi(state: VoiceModelManager.State) {
        val ctx = context ?: return
        val kind = VoiceModelManager.selectedKind()
        val mb = kind.approxSizeMb
        when (state) {
            VoiceModelManager.State.NotDownloaded -> {
                statusPref?.summary = ctx.getString(R.string.voice_model_status_missing, mb)
                downloadPref?.isEnabled = true
                downloadPref?.summary = ctx.getString(R.string.voice_model_download_summary, mb)
                deletePref?.isEnabled = false
            }
            is VoiceModelManager.State.Downloading -> {
                val text = if (state.totalBytes > 0) {
                    val pct = (state.downloadedBytes * 100 / state.totalBytes).toInt().coerceIn(0, 100)
                    ctx.getString(R.string.voice_model_status_downloading, pct)
                } else {
                    ctx.getString(
                        R.string.voice_model_status_downloading_bytes,
                        state.downloadedBytes / (1024L * 1024L),
                    )
                }
                statusPref?.summary = text
                downloadPref?.isEnabled = false
                downloadPref?.summary = text
                deletePref?.isEnabled = false
            }
            VoiceModelManager.State.Ready -> {
                statusPref?.summary = ctx.getString(
                    R.string.voice_model_ready_size,
                    formatSize(VoiceModelManager.modelSizeBytes(kind)),
                )
                downloadPref?.isEnabled = true
                downloadPref?.summary = ctx.getString(R.string.voice_model_redownload_summary)
                deletePref?.isEnabled = true
            }
            is VoiceModelManager.State.Error -> {
                statusPref?.summary = ctx.getString(R.string.voice_model_status_error, state.message)
                downloadPref?.isEnabled = true
                downloadPref?.summary = ""
                deletePref?.isEnabled = VoiceModelManager.modelSizeBytes(kind) > 0
            }
        }
    }

    private fun onDownloadClicked() {
        if (VoiceModelManager.isDownloadInProgress()) return
        val mb = VoiceModelManager.selectedKind().approxSizeMb
        when {
            VoiceModelManager.isReady() -> {
                // 已就绪：先删再下，等同重下
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.voice_model_download)
                    .setMessage(getString(R.string.voice_model_redownload_confirm, mb))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        VoiceModelManager.deleteModel()
                        startDownload()
                    }
                    .show()
            }
            else -> startDownload()
        }
    }

    private fun startDownload() {
        val mb = VoiceModelManager.selectedKind().approxSizeMb
        requireContext().toast(getString(R.string.voice_input_model_download_start, mb))
        VoiceModelManager.ensureDownloaded(
            onSuccess = { context?.toast(R.string.voice_input_model_download_done) },
            onFailure = { context?.toast(R.string.voice_input_model_download_error) },
        )
    }

    private fun onDeleteClicked() {
        val ctx = context ?: return
        val mb = VoiceModelManager.selectedKind().approxSizeMb
        AlertDialog.Builder(ctx)
            .setTitle(R.string.voice_model_delete)
            .setMessage(getString(R.string.voice_model_delete_confirm, mb))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                VoiceModelManager.deleteModel()
                ctx.toast(R.string.voice_model_deleted)
            }
            .show()
    }

    private fun formatSize(bytes: Long): String {
        return if (bytes < 1024 * 1024L) "${bytes / 1024} KB"
        else "${bytes / (1024 * 1024)} MB"
    }
}
