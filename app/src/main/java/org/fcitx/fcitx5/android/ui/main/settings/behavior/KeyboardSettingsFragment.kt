/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment

/** 虚拟键盘设置。语音相关项已迁到 [VoiceSettingsFragment]。 */
class KeyboardSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().keyboard)
