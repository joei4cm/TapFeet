/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.CandidateFrequency
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.CandidateFont
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.displayComment
import splitties.views.backgroundColor
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent

/**
 * Floating (physical-keyboard) candidate item.
 *
 * Layout: `序号+词` over a smaller 拼音/翻译 line. Preferred state is accent text + bold only
 * (no underline / fill — those either covered the gloss or raised the row until the window
 * clipped). Window positioning clamps the taller two-line cell on-screen.
 */
class LabeledCandidateItemUi(
    override val ctx: Context,
    val theme: Theme,
    setupTextView: TextView.() -> Unit
) : Ui {

    private val label = textView {
        setupTextView(this)
        includeFontPadding = false
        backgroundColor = Color.TRANSPARENT
    }

    private val comment = textView {
        setupTextView(this)
        includeFontPadding = true
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * 0.55f)
        setTextColor(theme.candidateCommentColor)
        visibility = View.GONE
        backgroundColor = Color.TRANSPARENT
    }

    override val root = view(::LinearLayout) {
        orientation = LinearLayout.VERTICAL
        backgroundColor = Color.TRANSPARENT
        clipToPadding = false
        clipChildren = false
        add(label, lParams(wrapContent, wrapContent))
        add(comment, lParams(wrapContent, wrapContent))
    }

    fun update(candidate: CandidateWord, active: Boolean) {
        val prefs = AppPrefs.getInstance().candidateBar
        val baseFg = if (prefs.candidateFrequencyColor.getValue()) {
            CandidateFrequency.colorFor(
                CandidateFrequency.countOf(candidate.text),
                theme.candidateTextColor,
                theme.genericActiveBackgroundColor,
            )
        } else {
            theme.candidateTextColor
        }
        val fg = if (active) {
            CandidateFrequency.readableAccent(baseFg, theme.genericActiveBackgroundColor)
        } else {
            baseFg
        }
        val labelFg = if (active) {
            ColorUtils.setAlphaComponent(fg, 0xCC)
        } else {
            theme.candidateLabelColor
        }
        val altFg = if (active) {
            ColorUtils.setAlphaComponent(fg, 0xB3)
        } else {
            theme.candidateCommentColor
        }
        label.text = buildSpannedString {
            color(labelFg) {
                append(candidate.label)
            }
            color(fg) {
                append(candidate.text)
            }
        }
        val showComment = prefs.showCandidateComment.getValue()
        val hint = if (showComment) candidate.displayComment() else ""
        if (hint.isNotBlank()) {
            comment.visibility = View.VISIBLE
            comment.text = hint
            comment.setTextColor(altFg)
        } else {
            comment.visibility = View.GONE
            comment.text = ""
        }
        label.typeface = CandidateFont.typeface(ctx, active || prefs.candidateFontBold.getValue())
        comment.typeface = CandidateFont.typeface(ctx, bold = false)
        label.letterSpacing = prefs.candidateLetterSpacing.getValue() / 100f
        root.backgroundColor = Color.TRANSPARENT
    }
}
