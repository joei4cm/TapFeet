/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.shortcut

/**
 * Hardware [ShortcutAction.VoiceInput] is hold-to-talk: key-down starts a session, key-up stops it.
 * The toolbar mic stays a tap toggle ([org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent.toggleVoiceInput]).
 *
 * Resolver matching must happen on the DOWN event — chord prefixes (Sym / Fn) are cleared on UP
 * before the next dispatch step. Callers remember the keyCode from DOWN and invoke release on that
 * key's UP without resolving the chord again.
 */
object VoiceShortcut {

    enum class Command { Start, Stop, Ignore }

    fun onDown(recording: Boolean): Command =
        if (recording) Command.Ignore else Command.Start

    fun onUp(recording: Boolean): Command =
        if (recording) Command.Stop else Command.Ignore
}
