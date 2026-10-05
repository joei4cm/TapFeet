/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import androidx.core.graphics.withSave
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Single-line text that can shrink to fit its width.
 *
 * Drawing goes through [StaticLayout] so Android's CJK fallback fonts apply. The previous
 * [Canvas.drawText] path used only the primary typeface, which turns Han characters into tofu
 * boxes on devices whose default face is Latin-first (Unihertz Titan Elite).
 */
@SuppressLint("AppCompatCustomView")
class AutoScaleTextView @JvmOverloads constructor(
    context: Context?,
    attributeSet: AttributeSet? = null
) : TextView(context, attributeSet) {

    enum class Mode {
        /**
         * do not scale or ellipse text, overflow when cannot fit width
         */
        None,

        /**
         * only scale in X axis, makes text looks "condensed" or "slim"
         */
        Horizontal,

        /**
         * scale both in X and Y axis, align center vertically
         */
        Proportional
    }

    var scaleMode = Mode.None

    private var needsMeasureText = true
    private val fontMetrics = Paint.FontMetrics()
    private val textBounds = Rect()

    private var needsCalculateTransform = true
    private var baselineX = 0.0f
    private var baselineY = 0.0f
    private var textScaleX = 1.0f
    private var textScaleY = 1.0f

    override fun setText(charSequence: CharSequence?, bufferType: BufferType) {
        if (charSequence == null || !text.contentEquals(charSequence)) {
            needsMeasureText = true
            needsCalculateTransform = true
            super.setText(charSequence, bufferType)
            requestLayout()
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        needsMeasureText = true
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val width = measureTextBounds().width() + paddingLeft + paddingRight
        val height = ceil(fontMetrics.bottom - fontMetrics.top + paddingTop + paddingBottom).toInt()
        val maxHeight = if (maxHeight >= 0) maxHeight else Int.MAX_VALUE
        val maxWidth = if (maxWidth >= 0) maxWidth else Int.MAX_VALUE
        setMeasuredDimension(
            measure(widthMode, widthSize, min(max(width, minimumWidth), maxWidth)),
            measure(heightMode, heightSize, min(max(height, minimumHeight), maxHeight))
        )
    }

    private fun measure(specMode: Int, specSize: Int, calculatedSize: Int): Int = when (specMode) {
        MeasureSpec.EXACTLY -> specSize
        MeasureSpec.AT_MOST -> min(calculatedSize, specSize)
        else -> calculatedSize
    }

    private fun layoutFor(src: String): StaticLayout {
        val tp = paint as TextPaint
        return StaticLayout.Builder
            .obtain(src, 0, src.length, tp, LAYOUT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()
    }

    private fun measureTextBounds(): Rect {
        if (needsMeasureText) {
            val paint = paint
            paint.getFontMetrics(fontMetrics)
            val src = text.toString()
            if (src.isEmpty()) {
                textBounds.setEmpty()
            } else {
                val layout = layoutFor(src)
                val left = floor(layout.getLineLeft(0)).toInt()
                val right = ceil(layout.getLineRight(0)).toInt()
                textBounds.set(
                    left,
                    floor(fontMetrics.top).toInt(),
                    right,
                    ceil(fontMetrics.bottom).toInt()
                )
            }
            needsMeasureText = false
        }
        return textBounds
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (needsCalculateTransform || changed) {
            calculateTransform(right - left, bottom - top)
            needsCalculateTransform = false
        }
    }

    private fun calculateTransform(viewWidth: Int, viewHeight: Int) {
        val contentWidth: Int = viewWidth - paddingLeft - paddingRight
        val contentHeight: Int = viewHeight - paddingTop - paddingBottom
        measureTextBounds()
        val textLeft: Float = textBounds.left.toFloat()
        val textWidth: Float = textBounds.width().toFloat()
        val textTop: Float = fontMetrics.top
        val textHeight: Float = fontMetrics.bottom - fontMetrics.top
        if (textWidth > contentWidth) {
            when (scaleMode) {
                Mode.None -> {
                    textScaleX = 1.0f
                    textScaleY = 1.0f
                }
                Mode.Horizontal -> {
                    textScaleX = contentWidth / textWidth
                    textScaleY = 1.0f
                }
                Mode.Proportional -> {
                    val scale = contentWidth / textWidth
                    textScaleX = scale
                    textScaleY = scale
                }
            }
        } else {
            textScaleX = 1.0f
            textScaleY = 1.0f
        }
        baselineX = calculateBaselineX(paddingLeft, contentWidth, textLeft, textWidth, textScaleX)
        baselineY = calculateBaselineY(paddingTop, contentHeight, textTop, textHeight, textScaleY)
    }

    private fun calculateBaselineX(
        contentLeft: Int,
        contentWidth: Int,
        textLeft: Float,
        textWidth: Float,
        scaleX: Float
    ): Float {
        val scaledTextLeft = textLeft * scaleX
        val scaledTextWidth = textWidth * scaleX
        val horizontalGravity =
            Gravity.getAbsoluteGravity(gravity, layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK
        val targetLeft: Float = @SuppressLint("RtlHardcoded") when (horizontalGravity) {
            Gravity.LEFT -> contentLeft.toFloat()
            Gravity.RIGHT -> contentLeft + contentWidth - scaledTextWidth
            else -> contentLeft + (contentWidth - scaledTextWidth) / 2.0f
        }
        return targetLeft - scaledTextLeft
    }

    private fun calculateBaselineY(
        contentTop: Int,
        contentHeight: Int,
        textTop: Float,
        textHeight: Float,
        scaleY: Float
    ): Float {
        val scaledTextTop = textTop * scaleY
        val scaledTextHeight = textHeight * scaleY
        val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK
        val targetTop: Float = when (verticalGravity) {
            Gravity.TOP -> contentTop.toFloat()
            Gravity.BOTTOM -> contentTop + contentHeight - scaledTextHeight
            else -> contentTop + (contentHeight - scaledTextHeight) / 2.0f
        }
        return targetTop - scaledTextTop
    }

    override fun onDraw(canvas: Canvas) {
        if (needsCalculateTransform) {
            calculateTransform(width, height)
            needsCalculateTransform = false
        }
        val src = text.toString()
        if (src.isEmpty()) return
        val layout = layoutFor(src)
        val tp = paint as TextPaint
        tp.color = currentTextColor
        canvas.withSave {
            translate(scrollX.toFloat(), scrollY.toFloat())
            translate(baselineX, baselineY)
            scale(textScaleX, textScaleY)
            translate(-layout.getLineLeft(0), -layout.getLineBaseline(0).toFloat())
            layout.draw(this)
        }
    }

    override fun getBaseline(): Int {
        return paddingTop + baselineY.roundToInt()
    }

    companion object {
        /** Wide enough that a single line never wraps; StaticLayout still measures real glyph width. */
        private const val LAYOUT_WIDTH = 1 shl 20
    }
}
