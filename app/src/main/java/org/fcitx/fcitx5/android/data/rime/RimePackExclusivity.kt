/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.rime

import org.fcitx.fcitx5.android.input.keyboard.PinyinEngineKind

/** 雾凇 / 白霜 / 薄荷共用 `data/rime/`，同时只保留一套包的安装标记。 */
object RimePackExclusivity {

    fun clearOthers(installing: PinyinEngineKind) {
        if (installing != PinyinEngineKind.RimeIce) RimeIceManager.clearInstallMarker()
        if (installing != PinyinEngineKind.RimeFrost) RimeFrostManager.clearInstallMarker()
        if (installing != PinyinEngineKind.RimeMint) RimeMintManager.clearInstallMarker()
    }
}
