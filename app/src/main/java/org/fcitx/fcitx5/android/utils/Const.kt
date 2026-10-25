/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

import org.fcitx.fcitx5.android.BuildConfig

object Const {
    const val versionName = "${BuildConfig.VERSION_NAME}-${BuildConfig.BUILD_TYPE}"
    const val githubRepo = "https://github.com/joei4cm/TapFeet"
    const val licenseSpdxId = "LGPL-2.1-or-later"
    const val licenseUrl = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1"
    const val privacyPolicyUrl = "https://github.com/joei4cm/TapFeet"
    const val faqUrl = "https://fcitx5-android.github.io/faq/"

    /** Repo used by the in-app updater (GitHub Releases API). */
    const val githubUpdateRepo = "joei4cm/TapFeet"
    const val githubReleasesLatestApi =
        "https://api.github.com/repos/$githubUpdateRepo/releases/latest"
    const val githubReleasesListApi =
        "https://api.github.com/repos/$githubUpdateRepo/releases?per_page=15"
}
