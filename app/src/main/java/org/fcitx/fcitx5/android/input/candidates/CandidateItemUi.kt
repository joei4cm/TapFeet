/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.CandidateFrequency
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
 * 候选项：竖排「字 / 拼音·翻译」。
 *
 * 选中态只用强调色 + 加粗。竖排注释需要比默认 40dp 更高的行高，见 [rowHeightDp]。
 */
class CandidateItemUi(override val ctx: Context, val theme: Theme) : Ui {

    companion object {
        /** Idle / 无注释时的候选行高（dp）。 */
        const val ROW_HEIGHT_DP = 40

        /**
         * 开启「显示注释/翻译」时的行高（dp）。
         * 20sp 汉字 + ~10sp 注释 + 字体内边距，40dp 会把翻译底边裁掉。
         */
        const val ROW_HEIGHT_WITH_COMMENT_DP = 56

        fun rowHeightDp(showComment: Boolean): Int =
            if (showComment) ROW_HEIGHT_WITH_COMMENT_DP else ROW_HEIGHT_DP
    }

    private val index = textView {
        textSize = 7f // sp — badge default; prefs can nudge within a small range
        setTextColor(
            ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(theme.candidateLabelColor, theme.candidateCommentColor, 0.55f),
                0x99
            )
        )
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
        // Keep font padding so Latin gloss descenders (g/y/p) are not clipped.
        includeFontPadding = true
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

    /**
     * Thin start/top gutter so the index sits outside the glyph box instead of covering it.
     * clipChildren off so a slightly oversized badge can peek into item padding.
     */
    private val content = view(::FrameLayout) {
        val gutterStart = dp(7)
        val gutterTop = dp(2)
        setPadding(gutterStart, gutterTop, 0, 0)
        clipToPadding = false
        clipChildren = false
        add(wordColumn, lParams(wrapContent, wrapContent) {
            gravity = Gravity.CENTER
        })
        add(index, lParams(wrapContent, wrapContent) {
            gravity = Gravity.TOP or Gravity.START
            // Sit in the gutter, slightly above/left of the word.
            marginStart = -gutterStart + dp(1)
            topMargin = -gutterTop
        })
    }

    private val pressHighlight = pressHighlightDrawable(theme.keyPressHighlightColor)

    override val root = view(::CustomGestureView) {
        background = pressHighlight
        clipChildren = false
        clipToPadding = false

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
        indexFontSize: Int = 7,
        textFontSize: Int = 20,
    ) {
        val prefs = org.fcitx.fcitx5.android.data.prefs.AppPrefs.getInstance().candidateBar
        val showComment = prefs.showCandidateComment.getValue()
        index.text = if (showIndex && indexLabel.isNotBlank()) indexLabel else ""
        index.visibility = if (index.text.isNotEmpty()) View.VISIBLE else View.GONE
        // Overlay badge stays small so it never swallows the first glyph.
        index.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            indexFontSize.coerceIn(6, 9).toFloat(),
        )
        text.text = candidate.text
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, textFontSize.toFloat())
        val face = CandidateFont.typeface(
            ctx, isActive || prefs.candidateFontBold.getValue()
        )
        text.typeface = face
        comment.typeface = face
        // Index stays light / non-bold so it reads as a caption, not part of the word.
        index.typeface = CandidateFont.typeface(ctx, bold = false)
        text.letterSpacing = prefs.candidateLetterSpacing.getValue() / 100f
        val baseFg = if (prefs.candidateFrequencyColor.getValue()) {
            CandidateFrequency.colorFor(
                CandidateFrequency.countOf(candidate.text),
                theme.candidateTextColor,
                theme.genericActiveBackgroundColor,
            )
        } else {
            theme.candidateTextColor
        }
        // Preferred = readable accent (not white-on-chip); others keep theme / frequency color.
        val textFg = if (isActive) {
            CandidateFrequency.readableAccent(baseFg, theme.genericActiveBackgroundColor)
        } else {
            baseFg
        }
        text.setTextColor(textFg)
        val hint = if (showComment) candidate.displayComment() else ""
        if (hint.isNotBlank()) {
            comment.visibility = View.VISIBLE
            comment.text = hint
            comment.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                max(8, textFontSize / 2).toFloat(),
            )
            comment.setTextColor(
                if (isActive) {
                    ColorUtils.setAlphaComponent(textFg, 0xB3)
                } else {
                    theme.candidateCommentColor
                },
            )
        } else {
            comment.visibility = View.GONE
            comment.text = ""
        }
        root.background = pressHighlight
    }
}
