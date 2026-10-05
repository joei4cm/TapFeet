/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.swipe

/**
 * Keyboard-surface paging direction. The selector maps left → the left-action binding and
 * right → the right-action binding; those bindings (PageNext / PagePrev) own the sign.
 * The swap pref then mirrors them. Do not also multiply in the selector's raw ±1 — that made
 * both swipes go forward with the default left=next / right=prev bindings.
 */
object FlyTextPaging {

    fun delta(action: FlyTextAction, swap: Boolean): Int? = when (action) {
        FlyTextAction.PageNext -> if (swap) -1 else 1
        FlyTextAction.PagePrev -> if (swap) 1 else -1
        else -> null
    }
}
