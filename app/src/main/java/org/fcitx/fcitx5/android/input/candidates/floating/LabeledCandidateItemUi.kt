/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
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
import splitties.views.dsl.core.textView

/**
 * Floating (physical-keyboard) candidate row item.
 *
 * Active state uses a thin accent underline — not a full [Theme.genericActiveBackgroundColor]
 * fill. A solid strip under the preedit row looked like a bar covering the pinyin.
 */
class LabeledCandidateItemUi(
    override val ctx: Context,
    val theme: Theme,
    setupTextView: TextView.() -> Unit
) : Ui {

    override val root = textView {
        setupTextView(this)
    }

    private val borderThickness: Int by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            ctx.resources.displayMetrics,
        ).toInt()
    }

    private val activeUnderline by lazy {
        val border = ColorDrawable(
            CandidateFrequency.readableAccent(
                theme.candidateTextColor,
                theme.genericActiveBackgroundColor,
            )
        )
        LayerDrawable(arrayOf(border)).apply {
            setLayerGravity(0, Gravity.BOTTOM)
            setLayerHeight(0, borderThickness)
        }
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
        root.text = buildSpannedString {
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
            root.background = activeUnderline
        } else {
            root.backgroundColor = Color.TRANSPARENT
        }
        root.typeface = CandidateFont.typeface(ctx, active || prefs.candidateFontBold.getValue())
        root.letterSpacing = prefs.candidateLetterSpacing.getValue() / 100f
    }
}
