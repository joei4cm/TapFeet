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
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.CandidateFrequency
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.CandidateFont
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.displayComment
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent

/**
 * Floating (physical-keyboard) candidate row item.
 *
 * Active state is a thin accent underline **below** the glyphs — never a full
 * [Theme.genericActiveBackgroundColor] fill (that covered pinyin), and not a
 * [LayerDrawable] bottom strip on the TextView itself (on some devices the color
 * still filled the whole cell and painted over the candidate — reported on Titan).
 */
class LabeledCandidateItemUi(
    override val ctx: Context,
    val theme: Theme,
    setupTextView: TextView.() -> Unit
) : Ui {

    private val label = textView {
        setupTextView(this)
        // Bottom padding reserved for [underline]; setupTextView's vertical pad stays for top/sides.
        includeFontPadding = false
    }

    private val underlineThickness: Int by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f,
            ctx.resources.displayMetrics,
        ).toInt().coerceAtLeast(1)
    }

    private val underlineGap: Int by lazy { ctx.dp(1) }

    private val underline = view(::View) {
        visibility = View.INVISIBLE
        backgroundColor = Color.TRANSPARENT
    }

    override val root = view(::LinearLayout) {
        orientation = LinearLayout.VERTICAL
        // Transparent hit target; underline is a child, not a TextView background fill.
        backgroundColor = Color.TRANSPARENT
        add(label, lParams(wrapContent, wrapContent))
        add(underline, lParams(matchParent, underlineThickness) {
            topMargin = underlineGap
        })
    }

    fun update(candidate: CandidateWord, active: Boolean) {
        val prefs = AppPrefs.getInstance().candidateBar
        // Keep theme text colors even when active — white-on-accent was for the old fill chip.
        val labelFg = theme.candidateLabelColor
        val fg = if (prefs.candidateFrequencyColor.getValue()) {
            CandidateFrequency.colorFor(
                CandidateFrequency.countOf(candidate.text),
                theme.candidateTextColor,
                theme.genericActiveBackgroundColor,
            )
        } else {
            theme.candidateTextColor
        }
        val altFg = theme.candidateCommentColor
        label.text = buildSpannedString {
            color(labelFg) {
                append(candidate.label)
            }
            color(fg) {
                append(candidate.text)
            }
            val showComment = AppPrefs.getInstance().candidateBar.showCandidateComment.getValue()
            val hint = if (showComment) candidate.displayComment() else ""
            if (hint.isNotBlank()) {
                if (candidate.spaceBetweenComment) {
                    append(" ")
                }
                color(altFg) {
                    append(hint)
                }
            }
        }
        if (active) {
            underline.visibility = View.VISIBLE
            underline.backgroundColor = CandidateFrequency.readableAccent(
                theme.candidateTextColor,
                theme.genericActiveBackgroundColor,
            )
        } else {
            underline.visibility = View.INVISIBLE
            underline.backgroundColor = Color.TRANSPARENT
        }
        label.typeface = CandidateFont.typeface(ctx, active || prefs.candidateFontBold.getValue())
        label.letterSpacing = prefs.candidateLetterSpacing.getValue() / 100f
    }
}
