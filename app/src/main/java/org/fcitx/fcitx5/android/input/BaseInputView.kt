/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.view.View
import android.view.WindowInsets
import android.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.reloadPinyinCustomPhrase
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs
import org.fcitx.fcitx5.android.utils.item
import org.fcitx.fcitx5.android.utils.navbarFrameHeight
import splitties.resources.styledColor
import splitties.views.dsl.core.withTheme
import timber.log.Timber
import kotlin.math.max

abstract class BaseInputView(
    val service: FcitxInputMethodService,
    val fcitx: FcitxConnection,
    val theme: Theme
) : ConstraintLayout(service) {

    /**
     * Update UI (from cached events in FcitxAPI) to match fcitx's state, before ready to receive real events
     */
    protected abstract fun onStartHandleFcitxEvent()

    protected abstract fun handleFcitxEvent(it: FcitxEvent<*>)

    private var eventHandlerJob: Job? = null

    private fun setupFcitxEventHandler() {
        eventHandlerJob = service.lifecycleScope.launch {
            fcitx.runImmediately { eventFlow }.collect {
                handleFcitxEvent(it)
            }
        }
    }

    var handleEvents = false
        set(value) {
            field = value
            if (field) {
                Timber.d(
                    "CandSync: %s.handleEvents -> true (jobActive=%b)",
                    this::class.java.simpleName, eventHandlerJob?.isActive == true
                )
                onStartHandleFcitxEvent()
                // `eventHandlerJob != null` is NOT enough: the job can be dead (cancelled together
                // with its owning scope) while the field still holds it, and then the view would
                // stay deaf forever — no candidate event, frozen bar, while preedit keeps flowing
                // through InputConnection. Re-arm whenever the job is missing OR no longer active.
                if (eventHandlerJob?.isActive != true) {
                    setupFcitxEventHandler()
                }
            } else {
                Timber.d("CandSync: %s.handleEvents -> false", this::class.java.simpleName)
                eventHandlerJob?.cancel()
                eventHandlerJob = null
            }
        }

    private fun pinCustomPhrase(text: String) {
        val key = org.fcitx.fcitx5.android.data.pinyin.PinyinLookup.pinyinOf(text) ?: return
        try {
            val existing = (org.fcitx.fcitx5.android.data.pinyin.CustomPhraseManager.load()
                ?: emptyArray()).toMutableList()
            existing.removeAll { it.key == key && it.value == text }
            val order = (existing.maxOfOrNull { kotlin.math.abs(it.order) } ?: 0) + 1
            existing.add(
                0,
                org.fcitx.fcitx5.android.data.pinyin.customphrase.PinyinCustomPhrase(key, order, text)
            )
            org.fcitx.fcitx5.android.data.pinyin.CustomPhraseManager.save(existing.toTypedArray())
            fcitx.runIfReady { reloadPinyinCustomPhrase() }
        } catch (e: Exception) {
            Timber.e(e, "pinCustomPhrase")
        }
    }

    private fun deleteCustomPhrase(text: String) {
        try {
            val existing = (org.fcitx.fcitx5.android.data.pinyin.CustomPhraseManager.load()
                ?: emptyArray()).toMutableList()
            if (existing.none { it.value == text }) return
            existing.removeAll { it.value == text }
            org.fcitx.fcitx5.android.data.pinyin.CustomPhraseManager.save(existing.toTypedArray())
            fcitx.runIfReady { reloadPinyinCustomPhrase() }
        } catch (e: Exception) {
            Timber.e(e, "deleteCustomPhrase")
        }
    }

    private fun triggerCandidateAction(idx: Int, actionIdx: Int) {
        fcitx.runIfReady { triggerCandidateAction(idx, actionIdx) }
    }

    private var candidateActionMenu: PopupMenu? = null

    val themedContext = context.withTheme(R.style.Theme_InputViewTheme)

    fun showCandidateActionMenu(idx: Int, text: String, view: View) {
        candidateActionMenu?.dismiss()
        candidateActionMenu = null
        service.lifecycleScope.launch {
            val actions = fcitx.runOnReady { getCandidateActions(idx) }
            InputFeedbacks.hapticFeedback(view, longPress = true)
            candidateActionMenu = PopupMenu(themedContext, view).apply {
                menu.add(buildSpannedString {
                    bold {
                        color(context.styledColor(android.R.attr.colorAccent)) {
                            append(text)
                        }
                    }
                }).apply {
                    isEnabled = false
                }
                actions.forEach { action ->
                    menu.item(action.text) {
                        triggerCandidateAction(idx, action.id)
                    }
                }
                menu.item(context.getString(R.string.candidate_pin_phrase)) {
                    pinCustomPhrase(text)
                }
                menu.item(context.getString(R.string.candidate_delete_phrase)) {
                    deleteCustomPhrase(text)
                }
                setOnDismissListener {
                    candidateActionMenu = null
                }
                show()
            }
        }
    }

    private val navbarBackground by ThemeManager.prefs.navbarBackground

    protected fun getNavBarBottomInset(windowInsets: WindowInsets): Int {
        if (navbarBackground != ThemePrefs.NavbarBackground.Full) {
            return 0
        }
        val insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
        // use navigation bar insets when available
        val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        // in case navigation bar insets goes wrong (eg. on LineageOS 21+ with gesture navigation)
        // use mandatory system gesture insets
        val mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
        var insetsBottom = max(navBars.bottom, mandatory.bottom)
        if (insetsBottom <= 0) {
            // check system gesture insets and fallback to navigation_bar_frame_height just in case
            val gesturesBottom = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
            if (gesturesBottom > 0) {
                insetsBottom = max(gesturesBottom, context.navbarFrameHeight())
            }
        }
        return insetsBottom
    }

    private val ignoreSystemWindowInsets by AppPrefs.getInstance().advanced.ignoreSystemWindowInsets

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ignoreSystemWindowInsets) {
            // suppress view's own onApplyWindowInsets
            setOnApplyWindowInsetsListener { _, insets -> insets }
        } else {
            // on API 35+, we must call requestApplyInsets() manually after replacing views,
            // otherwise View#onApplyWindowInsets won't be called. ¯\_(ツ)_/¯
            requestApplyInsets()
        }
        // Attach-side self-heal, the direct counterpart of onDetachedFromWindow below: detach
        // cancels the fcitx-event collector and nothing re-arms it when the device mode never
        // changed (InputDeviceManager short-circuits on an unchanged value) — the view would stay
        // deaf forever, freezing the candidate bar while preedit keeps updating via
        // InputConnection. Reconcile here and the subscription survives any detach/attach cycle.
        // Safe by ordering: replaceInput{,Candidate}View registers with InputDeviceManager BEFORE
        // the framework attaches the view, so this always reconciles THIS instance, never a stale
        // outgoing one. Idempotent — it just re-pushes the current mode.
        service.reconcileInputViewEvents()
    }

    override fun onDetachedFromWindow() {
        handleEvents = false
        super.onDetachedFromWindow()
    }
}
