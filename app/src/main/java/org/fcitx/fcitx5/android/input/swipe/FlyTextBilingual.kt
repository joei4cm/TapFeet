/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.swipe

/**
 * What a down-swipe on the keyboard surface should do while bilingual fly-text is on.
 *
 * [panelPreedit] is the input-panel preedit: pinyin codes for 拼音, latin while English is
 * composing, empty when 联想 prediction is showing with nothing being typed.
 * [hasCandidates] is true when a candidate strip is visible.
 *
 * - Non-empty preedit → commit that latin as-is (skip the Chinese candidate).
 * - Empty preedit + candidates → dismiss the prediction strip (reset, no commit).
 * - Neither → switch 拼音/英语 (single-finger fallback when two-finger swipe and Alt+Space fail).
 *
 * The physical [org.fcitx.fcitx5.android.input.shortcut.ShortcutAction.CommitLatin] shortcut uses
 * [commitLatinOrDismissAction] instead, so idle does not leave pinyin.
 */
enum class FlyTextDownAction { CommitLatin, DismissPrediction, SwitchIme, None }

fun commitLatinOrDismissAction(panelPreedit: String, hasCandidates: Boolean): FlyTextDownAction =
    when {
        panelPreedit.isNotEmpty() -> FlyTextDownAction.CommitLatin
        hasCandidates -> FlyTextDownAction.DismissPrediction
        else -> FlyTextDownAction.None
    }

fun flyTextDownAction(panelPreedit: String, hasCandidates: Boolean): FlyTextDownAction =
    when (val action = commitLatinOrDismissAction(panelPreedit, hasCandidates)) {
        FlyTextDownAction.None -> FlyTextDownAction.SwitchIme
        else -> action
    }
