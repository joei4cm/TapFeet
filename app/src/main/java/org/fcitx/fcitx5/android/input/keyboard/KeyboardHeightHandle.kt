/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp
import kotlin.math.roundToInt

/**
 * Thin drag strip above the soft keyboard. Vertical drag rewrites
 * [AppPrefs.Keyboard.keyboardHeightPercent] (or the landscape twin) live.
 */
@SuppressLint("ViewConstructor")
class KeyboardHeightHandle(
    context: Context,
    private val theme: Theme,
    private val onHeightChanged: () -> Unit,
) : View(context) {

    private val prefs = AppPrefs.getInstance().keyboard
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.altKeyTextColor
        alpha = 90
        style = Paint.Style.FILL
    }
    private val pill = RectF()
    private var downY = 0f
    private var startPercent = 40
    private var dragging = false

    private fun activePref() = when (resources.configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> prefs.keyboardHeightPercentLandscape
        else -> prefs.keyboardHeightPercent
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = dp(HANDLE_DP)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pillW = dp(40).toFloat()
        val pillH = dp(4).toFloat()
        pill.set(
            (w - pillW) / 2f,
            (h - pillH) / 2f,
            (w + pillW) / 2f,
            (h + pillH) / 2f,
        )
        canvas.drawRoundRect(pill, pillH, pillH, trackPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downY = event.rawY
                startPercent = activePref().getValue()
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                val dy = downY - event.rawY // drag up → taller keyboard
                val screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
                val deltaPercent = (dy * 100f / screenH).roundToInt()
                val next = (startPercent + deltaPercent).coerceIn(MIN_PERCENT, MAX_PERCENT)
                val pref = activePref()
                if (pref.getValue() != next) {
                    pref.setValue(next)
                    onHeightChanged()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        const val HANDLE_DP = 16
        const val MIN_PERCENT = 10
        const val MAX_PERCENT = 90
    }
}
