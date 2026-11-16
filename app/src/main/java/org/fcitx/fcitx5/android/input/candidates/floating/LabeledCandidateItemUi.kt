/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.graphics.Color
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
import splitties.views.dsl.core.textView

/**
 * Floating (physical-keyboard) candidate item.
 *
 * Preferred state is **accent text color + bold** only — no underline / fill strip.
 * Underlines raised the row and clipped the floating window; fills covered glyphs/gloss.
 */
class LabeledCandidateItemUi(
    override val ctx: Context,
    val theme: Theme,
    setupTextView: TextView.() -> Unit
) : Ui {

    override val root = textView {
        setupTextView(this)
        includeFontPadding = false
        backgroundColor = Color.TRANSPARENT
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
        root.text = buildSpannedString {
            color(labelFg) {
                append(candidate.label)
            }
            color(fg) {
                append(candidate.text)
            }
            val showComment = prefs.showCandidateComment.getValue()
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
        root.typeface = CandidateFont.typeface(ctx, active || prefs.candidateFontBold.getValue())
        root.letterSpacing = prefs.candidateLetterSpacing.getValue() / 100f
        root.backgroundColor = Color.TRANSPARENT
    }
}
