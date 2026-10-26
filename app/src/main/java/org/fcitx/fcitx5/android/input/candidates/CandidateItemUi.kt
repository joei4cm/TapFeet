/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.CandidateFont
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.utils.pressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.gravityCenterHorizontal
import kotlin.math.max

/**
 * 候选项：序号在左，右侧竖排「字 / 拼音」，比左右并排更窄，一行能放下更多词。
 */
class CandidateItemUi(override val ctx: Context, val theme: Theme) : Ui {

    private val index = textView {
        textSize = 10f // sp
        setTextColor(ColorUtils.blendARGB(theme.candidateLabelColor, theme.candidateCommentColor, 0.5f))
        includeFontPadding = false
        gravity = gravityCenter
    }

    val text = view(::AutoScaleTextView) {
        scaleMode = AutoScaleTextView.Mode.Proportional
        textSize = 20f // sp
        isSingleLine = true
        includeFontPadding = false
        gravity = gravityCenter
        setTextColor(theme.candidateTextColor)
    }

    private val comment = textView {
        textSize = 10f
        isSingleLine = true
        includeFontPadding = false
        gravity = gravityCenter
        setTextColor(theme.candidateCommentColor)
        visibility = View.GONE
    }

    private val wordColumn = view(::LinearLayout) {
        orientation = LinearLayout.VERTICAL
        gravity = gravityCenterHorizontal
        add(text, lParams(wrapContent, wrapContent))
        add(comment, lParams(wrapContent, wrapContent))
    }

    private val content = view(::LinearLayout) {
        orientation = LinearLayout.HORIZONTAL
        gravity = gravityCenter
        add(index, lParams(wrapContent, wrapContent) {
            marginEnd = dp(2)
        })
        add(wordColumn, lParams(wrapContent, wrapContent))
    }

    private val pressHighlight = pressHighlightDrawable(theme.keyPressHighlightColor)

    private val borderThickness: Int by lazy {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, ctx.resources.displayMetrics).toInt()
    }

    private val activeBackground by lazy {
        val border = ColorDrawable(theme.genericActiveForegroundColor)
        LayerDrawable(arrayOf(pressHighlight, border)).apply {
            setLayerGravity(1, Gravity.BOTTOM)
            setLayerHeight(1, borderThickness)
        }
    }

    override val root = view(::CustomGestureView) {
        background = pressHighlight

        /**
         * candidate long press feedback is handled by [org.fcitx.fcitx5.android.input.BaseInputView.showCandidateActionMenu]
         */
        longPressFeedbackEnabled = false

        add(content, lParams(wrapContent, matchParent) {
            gravity = gravityCenter
        })
    }

    fun updateCandidate(
        candidate: CandidateWord,
        indexLabel: String = "",
        isActive: Boolean = false,
        showIndex: Boolean = true,
        indexFontSize: Int = 10,
        textFontSize: Int = 20,
    ) {
        val prefs = org.fcitx.fcitx5.android.data.prefs.AppPrefs.getInstance().candidateBar
        val showComment = prefs.showCandidateComment.getValue()
        index.text = if (showIndex && indexLabel.isNotBlank()) indexLabel else ""
        index.visibility = if (index.text.isNotEmpty()) View.VISIBLE else View.GONE
        index.setTextSize(TypedValue.COMPLEX_UNIT_SP, indexFontSize.toFloat())
        text.text = candidate.text
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, textFontSize.toFloat())
        val face = CandidateFont.typeface(
            ctx, isActive || prefs.candidateFontBold.getValue()
        )
        text.typeface = face
        comment.typeface = face
        index.typeface = face
        text.letterSpacing = prefs.candidateLetterSpacing.getValue() / 100f
        val hint = if (showComment) candidate.displayComment() else ""
        if (hint.isNotBlank()) {
            comment.visibility = View.VISIBLE
            comment.text = hint
            // 拼音略小于汉字；跟序号档位对齐，避免挤爆 40dp 候选栏
            comment.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                max(8, textFontSize / 2).toFloat(),
            )
        } else {
            comment.visibility = View.GONE
            comment.text = ""
        }
        if (isActive) {
            root.background = activeBackground
        } else {
            root.background = pressHighlight
        }
    }
}
