/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.swipe

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

/**
 * One keyboard-surface swipe can be bound to any of these. Defaults match the Elite
 * pinyin/English pairing: up = pick Chinese, down = latin/dismiss, left/right = page,
 * two-finger left/right = switch IME.
 */
enum class FlyTextAction(override val stringRes: Int) : ManagedPreferenceEnum {
    SelectCandidate(R.string.flytext_action_select),
    CommitLatinOrDismiss(R.string.flytext_action_latin),
    PageNext(R.string.flytext_action_page_next),
    PagePrev(R.string.flytext_action_page_prev),
    SwitchImeNext(R.string.flytext_action_ime_next),
    SwitchImePrev(R.string.flytext_action_ime_prev),
    Backspace(R.string.flytext_action_backspace),
    HideBar(R.string.flytext_action_hide_bar),
    VoiceInput(R.string.flytext_action_voice),
    PasteClipboard(R.string.flytext_action_paste_clipboard),
    None(R.string.flytext_action_none),
    ;

    /** True when this action should keep the keyboard-surface channel armed with no candidates. */
    fun keepsChannelWithoutCandidates(): Boolean = when (this) {
        CommitLatinOrDismiss, SwitchImeNext, SwitchImePrev, HideBar, Backspace, VoiceInput,
        PasteClipboard,
        -> true
        else -> false
    }
}
