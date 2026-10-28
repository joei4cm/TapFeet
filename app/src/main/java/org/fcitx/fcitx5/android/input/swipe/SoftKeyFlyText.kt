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
 * Only armed while [candidatesAvailable] and the shared 飞字 master switch is on. Returns true when
 * a swipe fires so the key suppresses its click / swipe-symbol for that stroke.
 */
class SoftKeyFlyText(
    private val density: Float,
    private val candidatesAvailable: () -> Boolean,
    private val candidateRectsProvider: () -> List<Pair<Int, Rect>>,
    private val onSelect: (Int) -> Unit,
    private val onPage: (Int) -> Unit,
) : OnGestureListener {

    private var downRawX = 0f
    private var downRawY = 0f
    private var armed = false
    private var fired = false
    private val loc = IntArray(2)

    override fun onGesture(view: View, event: CustomGestureView.Event): Boolean {
        if (!AppPrefs.getInstance().hardwareKeyboard.keyboardFlyText.getValue()) {
            armed = false
            return false
        }
        when (event.type) {
            GestureType.Down -> {
                if (!candidatesAvailable()) {
                    armed = false
                    return false
                }
                view.getLocationOnScreen(loc)
                downRawX = loc[0] + event.x
                downRawY = loc[1] + event.y
                armed = true
                fired = false
                return false
            }
            GestureType.Move, GestureType.Up -> {
                if (!armed || fired) return fired
                if (!candidatesAvailable()) {
                    armed = false
                    return false
                }
                view.getLocationOnScreen(loc)
                val rawX = loc[0] + event.x
                val rawY = loc[1] + event.y
                val scale = flyTextSensitivityScale(
                    AppPrefs.getInstance().hardwareKeyboard.keyboardFlyTextSensitivity.getValue()
                )
                val d = density * scale
                val dir = swipeDirection(rawX - downRawX, rawY - downRawY, d) ?: return false
                when (dir) {
                    SwipeDirection.UP -> {
                        val rects = candidateRectsProvider()
                        val idx = candidateIndexAtX(rawX, rects)
                        Timber.i("SoftFly: up-swipe → idx=$idx over ${rects.size} rects")
                        if (idx >= 0) {
                            onSelect(idx)
                            fired = true
                            return true
                        }
                    }
                    SwipeDirection.LEFT -> {
                        Timber.i("SoftFly: page next")
                        onPage(1)
                        fired = true
                        return true
                    }
                    SwipeDirection.RIGHT -> {
                        Timber.i("SoftFly: page prev")
                        onPage(-1)
                        fired = true
                        return true
                    }
                    SwipeDirection.DOWN -> return false
                }
                return false
            }
        }
    }
}
