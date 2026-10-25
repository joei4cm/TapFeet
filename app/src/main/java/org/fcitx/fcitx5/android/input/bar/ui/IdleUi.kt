/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui

import android.content.Context
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.Gravity
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.ViewAnimator
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.bar.ui.idle.ButtonsBarUi
import org.fcitx.fcitx5.android.input.bar.ui.idle.ClipboardSuggestionUi
import org.fcitx.fcitx5.android.input.bar.ui.idle.InlineSuggestionsUi
import org.fcitx.fcitx5.android.input.bar.ui.idle.NumberRow
import org.fcitx.fcitx5.android.input.bar.ui.idle.SoftToolbarUi
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.imageResource
import timber.log.Timber

class IdleUi(
    override val ctx: Context,
    private val theme: Theme,
    private val popup: PopupComponent,
    private val commonKeyActionListener: CommonKeyActionListener
) : Ui {

    enum class State {
        Empty, Toolbar, Clipboard, NumberRow, InlineSuggestion
    }

    var currentState = State.Empty
        private set

    /**
     * 可被「隐藏状态栏」整行收起的子态。
     * SoftToolbar / ButtonsBar 在软键盘展开时仍会由 KawaiiBar 强制保留；
     * 剪贴板建议、密码数字行、内联建议是实质内容，不可收起。
     */
    val isDecorativeState: Boolean
        get() = currentState == State.Empty || currentState == State.Toolbar

    /** 子态切换回调：让 KawaiiBarComponent 在空闲子态变化后重算顶栏可见性。 */
    var onStateChanged: (() -> Unit)? = null

    private val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation

    private var inPrivate = false

    private val translateDirection by lazy {
        if (ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) 1f else -1f
    }

    private val menuButtonRotation
        get() = when {
            inPrivate -> 0f
            currentState == State.Toolbar -> 90f * translateDirection
            else -> -90f * translateDirection
        }

    val menuButton = ToolButton(ctx, R.drawable.ic_baseline_expand_more_24, theme).apply {
        rotation = menuButtonRotation
    }

    /** Gboard 风格空闲条：设置 / 主题 / 语音等入口。 */
    val softToolbar = SoftToolbarUi(ctx, theme)

    /** 兼容旧引用：设置齿轮挂在 SoftToolbar 上。 */
    val settingsButton get() = softToolbar.settingsButton

    val keyboardToggleButton = ToolButton(ctx, R.drawable.ic_baseline_keyboard_24, theme)

    /** 兼容旧引用：语音槽位挂在 SoftToolbar 上。 */
    val voiceInputButton get() = softToolbar.voiceInputButton
    val voiceWaveView get() = softToolbar.voiceWaveView
    val voiceInputSlot get() = softToolbar.voiceInputSlot

    val inputMethodButton = ToolButton(ctx, R.drawable.ic_status_pinyin, theme)

    val altLockButton = ToolButton(ctx, R.drawable.ic_alt_key_24, theme)

    /** 常驻大写指示按钮（锁定时显示），与 Alt 锁定按钮同一套显示逻辑。 */
    val capsLockButton = ToolButton(ctx, R.drawable.ic_caps_lock_24, theme).apply {
        visibility = View.GONE
    }

    val hideKeyboardButton = ToolButton(ctx, R.drawable.ic_baseline_arrow_drop_down_24, theme)

    /**
     * 「快捷窗口」常驻触发按钮：点一次按排序轮换 符号→表情→自定义→关闭；图标随当前面板变化。
     * 在状态栏常驻显示，循环仍可由屏幕 `!?#` 键与物理 SYM 键触发。
     */
    val panelCycleButton = ToolButton(ctx, R.drawable.ic_baseline_view_module_24, theme).apply {
        contentDescription = ctx.getString(R.string.panel_cycle)
        visibility = View.VISIBLE
    }

    val buttonsUi = ButtonsBarUi(ctx, theme)

    val clipboardUi = ClipboardSuggestionUi(ctx, theme)

    val numberRow = NumberRow(ctx, theme).apply {
        visibility = View.GONE
    }

    val inlineSuggestionsBar = InlineSuggestionsUi(ctx)

    private val animator = ViewAnimator(ctx).apply {
        // index 0 占位：Empty 时改走 softToolbar 全宽覆盖，不经过 animator
        add(View(ctx), lParams(matchParent, matchParent))
        add(buttonsUi.root, lParams(matchParent, matchParent))
        add(clipboardUi.root, lParams(matchParent, matchParent))
        add(inlineSuggestionsBar.root, lParams(matchParent, matchParent))
    }

    private val inAnimation by lazy {
        AnimationSet(true).apply {
            duration = 200L
            addAnimation(AlphaAnimation(0f, 1f))
            // 2 stands for Animation.RELATIVE_TO_PARENT
            addAnimation(TranslateAnimation(2, -0.3f * translateDirection, 2, 0f, 0, 0f, 0, 0f))
        }
    }

    private val outAnimation by lazy {
        AnimationSet(true).apply {
            duration = 200L
            addAnimation(AlphaAnimation(1f, 0f))
            addAnimation(TranslateAnimation(2, 0f, 2, -0.3f * translateDirection, 0, 0f, 0, 0f))
        }
    }

    private val idleBody = constraintLayout {
        val size = dp(KawaiiBarComponent.HEIGHT)
        add(menuButton, lParams(size, size) {
            startOfParent()
            centerVertically()
        })
        add(hideKeyboardButton, lParams(size, size) {
            endOfParent()
            centerVertically()
        })
        add(keyboardToggleButton, lParams(size, size) {
            before(hideKeyboardButton)
            centerVertically()
        })
        add(inputMethodButton, lParams(size, size) {
            before(panelCycleButton)
            centerVertically()
        })
        add(panelCycleButton, lParams(size, size) {
            before(keyboardToggleButton)
            centerVertically()
        })
        add(altLockButton, lParams(size, size) {
            before(inputMethodButton)
            centerVertically()
        })
        add(capsLockButton, lParams(size, size) {
            before(altLockButton)
            centerVertically()
        })
        add(animator, lParams(matchConstraints, matchParent) {
            after(menuButton)
            before(capsLockButton)
            centerVertically()
        })
    }

    override val root = frameLayout {
        add(idleBody, lParams(matchParent, matchParent))
        add(softToolbar.root, lParams(matchParent, matchParent))
        add(numberRow, lParams(matchParent, matchParent))
    }

    init {
        // 默认 Empty：全宽 SoftToolbar
        idleBody.visibility = View.GONE
        softToolbar.root.visibility = View.VISIBLE
        numberRow.visibility = View.GONE
    }

    fun privateMode(activate: Boolean = true) {
        if (activate == inPrivate) return
        inPrivate = activate
        updateMenuButtonIcon()
        updateMenuButtonContentDescription()
        updateMenuButtonRotation(instant = true)
    }

    private fun updateMenuButtonIcon() {
        menuButton.image.imageResource =
            if (inPrivate) R.drawable.ic_view_private
            else R.drawable.ic_baseline_expand_more_24
    }

    private fun updateMenuButtonContentDescription() {
        menuButton.contentDescription = when {
            inPrivate -> ctx.getString(R.string.private_mode)
            currentState == State.Toolbar -> ctx.getString(R.string.hide_toolbar)
            else -> ctx.getString(R.string.expand_toolbar)
        }
    }

    private fun updateMenuButtonRotation(instant: Boolean = false) {
        val targetRotation = menuButtonRotation
        menuButton.apply {
            if (targetRotation == rotation) return
            animate().cancel()
            if (!instant && !disableAnimation) {
                animate().setDuration(200L).rotation(targetRotation)
            } else {
                rotation = targetRotation
            }
        }
    }

    fun setHideKeyboardIsVoiceInput(isVoiceInput: Boolean, callback: View.OnClickListener) {
        if (isVoiceInput) {
            hideKeyboardButton.setIcon(R.drawable.ic_baseline_keyboard_voice_24)
            hideKeyboardButton.contentDescription = ctx.getString(R.string.switch_to_voice_input)
        } else {
            hideKeyboardButton.setIcon(R.drawable.ic_baseline_arrow_drop_down_24)
            hideKeyboardButton.contentDescription = ctx.getString(R.string.hide_keyboard)
        }
        hideKeyboardButton.setOnClickListener(callback)
    }

    fun updateAltLockButton(locked: Boolean) {
        if (locked) {
            altLockButton.visibility = View.VISIBLE
            altLockButton.setIcon(R.drawable.ic_alt_key_lock_24)
            altLockButton.contentDescription = ctx.getString(R.string.alt_lock_enabled)
        } else {
            altLockButton.visibility = View.GONE
            altLockButton.contentDescription = ctx.getString(R.string.alt_lock_disabled)
        }
    }

    fun updateCapsLockButton(locked: Boolean) {
        if (locked) {
            capsLockButton.visibility = View.VISIBLE
            capsLockButton.contentDescription = ctx.getString(R.string.caps_lock_enabled)
        } else {
            capsLockButton.visibility = View.GONE
            capsLockButton.contentDescription = ctx.getString(R.string.caps_lock_disabled)
        }
    }

    private fun clearAnimation() {
        animator.inAnimation = null
        animator.outAnimation = null
    }

    private fun setAnimation() {
        animator.inAnimation = inAnimation
        animator.outAnimation = outAnimation
    }

    private fun enableSlideTransition(inTarget: View, outTarget: View, inGravity: Int, outGravity: Int) {
        val slideIn = Slide(inGravity).apply { duration = 200L }
        val slideOut = Slide(outGravity).apply { duration = 200L }
        slideIn.addTarget(inTarget)
        slideOut.addTarget(outTarget)
        val set = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(slideIn)
            addTransition(slideOut)
        }
        TransitionManager.beginDelayedTransition(root, set)
    }

    fun updateState(state: State, fromUser: Boolean = false) {
        Timber.d("Switch idle ui to $state")
        if (
            !fromUser ||
            disableAnimation ||
            (state == State.InlineSuggestion || currentState == State.InlineSuggestion) ||
            (state == State.NumberRow || currentState == State.NumberRow) ||
            (state == State.Empty || currentState == State.Empty)
        ) {
            clearAnimation()
        } else {
            setAnimation()
        }
        when (state) {
            State.Empty -> {}
            State.Toolbar -> animator.displayedChild = 1
            State.Clipboard -> animator.displayedChild = 2
            State.NumberRow -> {}
            State.InlineSuggestion -> animator.displayedChild = 3
        }
        when (state) {
            State.Empty -> {
                softToolbar.root.visibility = View.VISIBLE
                idleBody.visibility = View.GONE
                numberRow.visibility = View.GONE
                numberRow.keyActionListener = null
                numberRow.popupActionListener = null
            }
            State.NumberRow -> {
                softToolbar.root.visibility = View.GONE
                numberRow.keyActionListener = commonKeyActionListener.listener
                numberRow.popupActionListener = popup.listener
                if (fromUser && !disableAnimation) {
                    val out = if (currentState == State.Empty) softToolbar.root else idleBody
                    enableSlideTransition(numberRow, out, Gravity.END, Gravity.START)
                }
                numberRow.visibility = View.VISIBLE
                idleBody.visibility = View.GONE
            }
            else -> {
                softToolbar.root.visibility = View.GONE
                if (currentState == State.NumberRow) {
                    if (fromUser && !disableAnimation) {
                        enableSlideTransition(idleBody, numberRow, Gravity.START, Gravity.END)
                    }
                    numberRow.keyActionListener = null
                    numberRow.popupActionListener = null
                    popup.dismissAll()
                }
                idleBody.visibility = View.VISIBLE
                numberRow.visibility = View.GONE
            }
        }
        currentState = state
        updateMenuButtonContentDescription()
        updateMenuButtonRotation(instant = !fromUser)
        onStateChanged?.invoke()
    }
}
