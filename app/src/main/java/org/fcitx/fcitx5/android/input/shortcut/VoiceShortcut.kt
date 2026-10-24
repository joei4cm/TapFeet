/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.shortcut

/**
 * Hardware [ShortcutAction.VoiceInput] is tap-toggle: key-down starts or stops a session;
 * key-up is ignored. Silence auto-stop ([org.fcitx.fcitx5.android.data.prefs.AppPrefs.Voice.voiceAutoStop])
 * ends a session without a second press. The toolbar mic uses the same toggle path
 * ([org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent.toggleVoiceInput]).
 *
 * Resolver matching must happen on the DOWN event — chord prefixes (Sym / Fn) are cleared on UP
 * before the next dispatch step. Callers remember the keyCode from DOWN and swallow that key's UP
 * (and auto-repeats) without resolving the chord again, so holding R cannot type `rrrr`.
 */
object VoiceShortcut {

    enum class Command { Start, Stop, Ignore }

    fun onDown(recording: Boolean): Command =
        if (recording) Command.Stop else Command.Start

    fun onUp(@Suppress("UNUSED_PARAMETER") recording: Boolean): Command = Command.Ignore
}
