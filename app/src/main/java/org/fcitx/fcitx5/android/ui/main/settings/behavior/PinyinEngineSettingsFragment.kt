/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.rime.RimeApplyState
import org.fcitx.fcitx5.android.data.rime.RimeDownloadablePack
import org.fcitx.fcitx5.android.data.rime.RimeFrostManager
import org.fcitx.fcitx5.android.data.rime.RimeIceManager
import org.fcitx.fcitx5.android.data.rime.RimeMintManager
import org.fcitx.fcitx5.android.input.keyboard.PinyinEngineKind
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim

/**
 * 中文拼音引擎选择（全局，与物理键盘无关）。
 * 下载态与加载态分开显示；点选启用并切换，Sym+N 才是输入法循环切换。
 */
class PinyinEngineSettingsFragment : PaddingPreferenceFragment() {

    private lateinit var hw: AppPrefs.HardwareKeyboard
    private val enginePrefs = mutableListOf<Preference>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        hw = AppPrefs.getInstance().hardwareKeyboard
        preferenceScreen = preferenceManager.createPreferenceScreen(context).apply {
            addPreference(Preference(context).apply {
                key = "pinyin_engine_help"
                title = getString(R.string.pinyin_engine)
                summary = getString(R.string.pinyin_engine_summary)
                isIconSpaceReserved = false
                isSingleLineTitle = false
                isSelectable = false
            })
            enginePrefs.clear()
            PinyinEngineKind.entries.forEach { kind ->
                val row = Preference(context).apply {
                    key = "pinyin_engine_pick_${kind.name}"
                    title = getString(kind.stringRes)
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    setOnPreferenceClickListener {
                        if (kind.needsDownload && !RimeDownloadablePack.isReady(kind)) {
                            val startRes = when (kind) {
                                PinyinEngineKind.RimeFrost ->
                                    R.string.pinyin_engine_rime_frost_download_start
                                PinyinEngineKind.RimeMint ->
                                    R.string.pinyin_engine_rime_mint_download_start
                                else -> R.string.pinyin_engine_rime_ice_download_start
                            }
                            Toast.makeText(
                                requireContext(),
                                getString(startRes, RimeDownloadablePack.approxSizeMb(kind)),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        val same = hw.pinyinEngine.getValue() == kind
                        hw.pinyinEngine.setValue(kind)
                        if (same) hw.pinyinEngine.fireChange()
                        refreshEngineSummaries()
                        if (!kind.needsDownload) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.pinyin_engine_loaded, getString(kind.stringRes)),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else if (RimeDownloadablePack.isReady(kind)) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.pinyin_engine_loading, getString(kind.stringRes)),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        true
                    }
                }
                enginePrefs += row
                addPreference(row)
            }
            addPreference(Preference(context).apply {
                key = "pinyin_engine_im_list"
                title = getString(R.string.pinyin_engine_more_im)
                summary = getString(R.string.pinyin_engine_more_im_summary)
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setOnPreferenceClickListener {
                    navigateWithAnim(SettingsRoute.InputMethodList)
                    true
                }
            })
            addPreference(Preference(context).apply {
                key = "pinyin_engine_plugins"
                title = getString(R.string.pinyin_engine_plugins)
                summary = getString(R.string.pinyin_engine_plugins_summary)
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setOnPreferenceClickListener {
                    navigateWithAnim(SettingsRoute.Plugin)
                    true
                }
            })
        }
        refreshEngineSummaries()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    RimeIceManager.state,
                    RimeFrostManager.state,
                    RimeMintManager.state,
                    RimeApplyState.applying,
                ) { _, _, _, _ -> }
                    .collect { refreshEngineSummaries() }
            }
        }
    }

    private fun refreshEngineSummaries() {
        if (enginePrefs.isEmpty()) return
        val current = hw.pinyinEngine.getValue()
        val iceState = RimeIceManager.state.value
        val frostState = RimeFrostManager.state.value
        val mintState = RimeMintManager.state.value
        enginePrefs.forEach { pref ->
            val kind = PinyinEngineKind.entries.first { "pinyin_engine_pick_${it.name}" == pref.key }
            pref.summary = summaryFor(kind, current, iceState, frostState, mintState)
        }
    }

    private fun summaryFor(
        kind: PinyinEngineKind,
        current: PinyinEngineKind,
        iceState: RimeIceManager.State,
        frostState: RimeFrostManager.State,
        mintState: RimeMintManager.State,
    ): String {
        if (RimeApplyState.isApplying(kind)) {
            return getString(R.string.pinyin_engine_status_loading)
        }
        if (kind == current) {
            val err = when (kind) {
                PinyinEngineKind.RimeIce -> (iceState as? RimeIceManager.State.Error)?.message
                PinyinEngineKind.RimeFrost -> (frostState as? RimeFrostManager.State.Error)?.message
                PinyinEngineKind.RimeMint -> (mintState as? RimeMintManager.State.Error)?.message
                else -> null
            }
            return if (err != null) {
                getString(R.string.pinyin_engine_status_load_failed, err)
            } else {
                getString(R.string.pinyin_engine_in_use)
            }
        }
        return when (kind) {
            PinyinEngineKind.RimeIce -> downloadableSummary(
                iceState,
                sizeMb = RimeIceManager.approxSizeMb(),
            )
            PinyinEngineKind.RimeFrost -> downloadableSummary(
                frostState,
                sizeMb = RimeFrostManager.approxSizeMb(),
            )
            PinyinEngineKind.RimeMint -> downloadableSummary(
                mintState,
                sizeMb = RimeMintManager.approxSizeMb(),
            )
            else -> getString(R.string.pinyin_engine_tap_to_load)
        }
    }

    private fun downloadableSummary(state: Any, sizeMb: Int): String {
        val downloading = when (state) {
            is RimeIceManager.State.Downloading -> state.downloadedBytes to state.totalBytes
            is RimeFrostManager.State.Downloading -> state.downloadedBytes to state.totalBytes
            is RimeMintManager.State.Downloading -> state.downloadedBytes to state.totalBytes
            else -> null
        }
        if (downloading != null) {
            val (done, total) = downloading
            val pct = if (total > 0) (done * 100 / total).toInt() else 0
            return getString(R.string.pinyin_engine_status_downloading, pct)
        }
        return when (state) {
            RimeIceManager.State.Ready,
            RimeFrostManager.State.Ready,
            RimeMintManager.State.Ready,
            -> getString(R.string.pinyin_engine_status_downloaded)
            is RimeIceManager.State.Error ->
                getString(R.string.pinyin_engine_status_download_failed, state.message)
            is RimeFrostManager.State.Error ->
                getString(R.string.pinyin_engine_status_download_failed, state.message)
            is RimeMintManager.State.Error ->
                getString(R.string.pinyin_engine_status_download_failed, state.message)
            else -> getString(R.string.pinyin_engine_status_need_download, sizeMb)
        }
    }
}
