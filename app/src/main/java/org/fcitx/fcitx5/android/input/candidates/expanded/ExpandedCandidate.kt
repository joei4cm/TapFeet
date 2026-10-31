/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.candidates.expanded

import org.fcitx.fcitx5.android.core.CandidateWord

/** 展开列表里的一条候选，[engineIndex] 是过滤英文词之前的引擎下标。 */
class ExpandedCandidate(
    val engineIndex: Int,
    val word: CandidateWord,
)
