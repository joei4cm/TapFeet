/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.bar.ui

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.horizontalLayout
import splitties.views.padding

class ChipStripUi(override val ctx: Context, private val theme: Theme) : Ui {

    private val row = ctx.horizontalLayout {
        orientation = LinearLayout.HORIZONTAL
    }

    override val root = HorizontalScrollView(ctx).apply {
        isHorizontalScrollBarEnabled = false
        visibility = View.GONE
        addView(
            row,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun update(items: List<Pair<String, () -> Unit>>) {
        row.removeAllViews()
        if (items.isEmpty()) {
            root.visibility = View.GONE
            return
        }
        items.forEach { (label, click) ->
            row.addView(TextView(ctx).apply {
                text = label
                setTextColor(theme.candidateTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                padding = ctx.dp(8)
                setOnClickListener { click() }
            })
        }
        root.visibility = View.VISIBLE
    }
}
