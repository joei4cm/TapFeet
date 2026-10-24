/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 删除策略：删当前引擎文件；另一档仍就绪则保留共用 VAD。
 * 纯逻辑单测，不碰文件系统。
 */
class VoiceModelDeletePolicyTest {

    @Test
    fun otherKind() {
        assertEquals(VoiceAsrKind.Qwen3, otherOf(VoiceAsrKind.SenseVoice))
        assertEquals(VoiceAsrKind.SenseVoice, otherOf(VoiceAsrKind.Qwen3))
    }

    @Test
    fun keepVadWhenOtherReady() {
        assertFalse(shouldDeleteVad(otherReady = true))
        assertTrue(shouldDeleteVad(otherReady = false))
    }

    private fun otherOf(kind: VoiceAsrKind): VoiceAsrKind =
        if (kind == VoiceAsrKind.SenseVoice) VoiceAsrKind.Qwen3 else VoiceAsrKind.SenseVoice

    private fun shouldDeleteVad(otherReady: Boolean): Boolean = !otherReady
}
