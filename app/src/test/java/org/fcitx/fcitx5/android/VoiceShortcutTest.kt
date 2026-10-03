/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.input.shortcut.VoiceShortcut
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceShortcutTest {

    @Test
    fun holdToTalkStartsOnDownWhenIdle() {
        assertEquals(VoiceShortcut.Command.Start, VoiceShortcut.onDown(false))
    }

    @Test
    fun holdToTalkIgnoresDownWhileAlreadyRecording() {
        assertEquals(VoiceShortcut.Command.Ignore, VoiceShortcut.onDown(true))
    }

    @Test
    fun holdToTalkStopsOnUpWhileRecording() {
        assertEquals(VoiceShortcut.Command.Stop, VoiceShortcut.onUp(true))
    }

    @Test
    fun holdToTalkIgnoresUpWhenIdle() {
        assertEquals(VoiceShortcut.Command.Ignore, VoiceShortcut.onUp(false))
    }
}
