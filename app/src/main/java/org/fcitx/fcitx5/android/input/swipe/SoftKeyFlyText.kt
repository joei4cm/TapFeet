/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.swipe

import android.graphics.Rect
import android.view.View
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.GestureType
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.OnGestureListener
import timber.log.Timber

/**
 * Soft-keyboard (letter/T9) fly-text: Titan Elite–style up-swipe by column to pick a candidate,
 * left/right to page. Driven from [CustomGestureView] key gestures (not the candidate bar).
 *
 * Important: classify on MOVE (consume so long-press popup / key click cannot steal the stroke),
 * but **commit only on UP**. Firing select mid-MOVE collapses the candidate bar under a live
 * finger — keys reflow / long-press popup appears ("keys stretch") and the touch stream dies so
 * the keyboard stops accepting input.
 */
class SoftKeyFlyText(
    private val density: Float,
    private val candidatesAvailable: () -> Boolean,
    private val candidateRectsProvider: () -> List<Pair<Int, Rect>>,
    private val onSelect: (Int) -> Unit,
    private val onPage: (Int) -> Unit,
    /** Dismiss long-press popup keyboards so they cannot cover the soft KB after a fly swipe. */
    private val onDismissPopups: () -> Unit = {},
) : OnGestureListener {

    private var downRawX = 0f
    private var downRawY = 0f
    private var armed = false
    /** Direction locked during MOVE; action runs on UP. */
    private var pendingDir: SwipeDirection? = null
    private var fired = false
    private val loc = IntArray(2)

    override fun onGesture(view: View, event: CustomGestureView.Event): Boolean {
        if (!AppPrefs.getInstance().hardwareKeyboard.keyboardFlyText.getValue()) {
            reset()
            return false
        }
        when (event.type) {
            GestureType.Down -> {
                if (!candidatesAvailable()) {
                    reset()
                    return false
                }
                view.getLocationOnScreen(loc)
                downRawX = loc[0] + event.x
                downRawY = loc[1] + event.y
                armed = true
                pendingDir = null
                fired = false
                return false
            }
            GestureType.Move -> {
                if (!armed || fired) return fired
                if (!candidatesAvailable()) {
                    reset()
                    return false
                }
                view.getLocationOnScreen(loc)
                val rawX = loc[0] + event.x
                val rawY = loc[1] + event.y
                val d = density * flyTextSensitivityScale(
                    AppPrefs.getInstance().hardwareKeyboard.keyboardFlyTextSensitivity.getValue()
                )
                val dx = rawX - downRawX
                val dy = rawY - downRawY
                // Drop lock if the finger returned to the origin (brush, not swipe).
                if (pendingDir != null && kotlin.math.hypot(dx, dy) < SWIPE_BASE_SLOP_DP * d) {
                    pendingDir = null
                    return false
                }
                if (pendingDir == null) {
                    val axis = swipeAxis(dx, dy, d) ?: return false
                    // Only claim vertical-up / horizontal — leave DOWN to swipe-symbol.
                    if (axis == SwipeDirection.DOWN) return false
                    pendingDir = axis
                    onDismissPopups()
                }
                // Consume so long-press popup / AltPreview cannot take over mid-swipe.
                return pendingDir != null
            }
            GestureType.Up -> {
                if (!armed) return false
                if (fired) {
                    reset()
                    return true
                }
                view.getLocationOnScreen(loc)
                val rawX = loc[0] + event.x
                val rawY = loc[1] + event.y
                val d = density * flyTextSensitivityScale(
                    AppPrefs.getInstance().hardwareKeyboard.keyboardFlyTextSensitivity.getValue()
                )
                val dx = rawX - downRawX
                val dy = rawY - downRawY
                val dir = pendingDir?.takeIf { swipeDirection(dx, dy, d) == it }
                    ?: swipeDirection(dx, dy, d)
                val consumed = when (dir) {
                    SwipeDirection.UP -> {
                        val rects = candidateRectsProvider()
                        val idx = candidateIndexAtX(rawX, rects)
                        Timber.i("SoftFly: up-swipe → idx=$idx over ${rects.size} rects")
                        if (idx >= 0) {
                            onDismissPopups()
                            onSelect(idx)
                            true
                        } else false
                    }
                    SwipeDirection.LEFT -> {
                        Timber.i("SoftFly: page next")
                        onDismissPopups()
                        onPage(1)
                        true
                    }
                    SwipeDirection.RIGHT -> {
                        Timber.i("SoftFly: page prev")
                        onDismissPopups()
                        onPage(-1)
                        true
                    }
                    SwipeDirection.DOWN, null -> false
                }
                reset()
                return consumed
            }
        }
    }

    private fun reset() {
        armed = false
        pendingDir = null
        fired = false
        downRawX = 0f
        downRawY = 0f
    }
}
