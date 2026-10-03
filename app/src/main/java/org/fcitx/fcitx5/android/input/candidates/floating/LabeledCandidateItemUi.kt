/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.floating

import android.content.Context
import android.graphics.Color
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.CandidateFont
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.displayComment
import splitties.views.backgroundColor
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.textView

class LabeledCandidateItemUi(
    override val ctx: Context,
    val theme: Theme,
    setupTextView: TextView.() -> Unit
) : Ui {

    override val root = textView {
        setupTextView(this)
    }

    fun update(candidate: CandidateWord, active: Boolean) {
        val labelFg = if (active) theme.genericActiveForegroundColor else theme.candidateLabelColor
        val fg = if (active) theme.genericActiveForegroundColor else theme.candidateTextColor
        val altFg = if (active) theme.genericActiveForegroundColor else theme.candidateCommentColor
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
        root.backgroundColor =
            if (active) theme.genericActiveBackgroundColor else Color.TRANSPARENT
        val prefs = AppPrefs.getInstance().candidateBar
        root.typeface = CandidateFont.typeface(ctx, active || prefs.candidateFontBold.getValue())
        root.letterSpacing = prefs.candidateLetterSpacing.getValue() / 100f
    }
}
