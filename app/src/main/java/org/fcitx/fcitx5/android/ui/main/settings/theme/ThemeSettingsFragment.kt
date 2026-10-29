/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.data.theme.ThemeManager

/** Soft-key chrome (radius, border, …). Day/night follow-system lives on the Theme list. */
class ThemeSettingsFragment : ManagedPreferenceFragment(ThemeManager.prefs)
