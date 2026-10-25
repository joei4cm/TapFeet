/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui.idle

import android.content.Context
import android.view.View
import androidx.annotation.DrawableRes
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.voice.VoiceWaveView
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view

/**
 * Gboard 风格空闲顶栏：整行均分图标（状态区 / 表情 / 剪贴板 / 设置 / 主题 / 语音）。
 * 打字出候选时由 [org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent] 切到 Candidate 行替换本条。
 */
class SoftToolbarUi(override val ctx: Context, private val theme: Theme) : Ui {

    override val root = view(::FlexboxLayout) {
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.SPACE_AROUND
    }

    private fun toolButton(@DrawableRes icon: Int) = ToolButton(ctx, icon, theme).also {
        val size = ctx.dp(40)
        root.addView(it, FlexboxLayout.LayoutParams(size, size))
    }

    val statusButton = toolButton(R.drawable.ic_baseline_view_module_24).apply {
        contentDescription = ctx.getString(R.string.status_area)
    }

    val emojiButton = toolButton(R.drawable.ic_baseline_tag_faces_24).apply {
        contentDescription = ctx.getString(R.string.panel_module_emoji)
    }

    val clipboardButton = toolButton(R.drawable.ic_clipboard).apply {
        contentDescription = ctx.getString(R.string.clipboard)
    }

    val settingsButton = toolButton(R.drawable.ic_baseline_settings_24).apply {
        contentDescription = ctx.getString(R.string.virtual_keyboard_settings)
    }

    val themeButton = toolButton(R.drawable.ic_baseline_palette_24).apply {
        contentDescription = ctx.getString(R.string.theme)
    }

    val voiceInputButton = ToolButton(ctx, R.drawable.ic_baseline_mic_24, theme).apply {
        contentDescription = ctx.getString(R.string.voice_input)
    }

    val voiceWaveView = VoiceWaveView(ctx).apply {
        visibility = View.GONE
        setBarColor(theme.altKeyTextColor)
    }

    /** 点按开始/结束语音；录音中与 [voiceWaveView] 互换显示。 */
    val voiceInputSlot = frameLayout {
        add(voiceInputButton, lParams(matchParent, matchParent))
        add(voiceWaveView, lParams(matchParent, matchParent))
        contentDescription = ctx.getString(R.string.voice_input)
    }.also {
        val size = ctx.dp(40)
        root.addView(it, FlexboxLayout.LayoutParams(size, size))
    }
}
