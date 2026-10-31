/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.expanded

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.CandidateItemUi
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder

open class PagingCandidateViewAdapter(val theme: Theme) :
    PagingDataAdapter<ExpandedCandidate, CandidateViewHolder>(diffCallback) {

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<ExpandedCandidate>() {
            override fun areItemsTheSame(oldItem: ExpandedCandidate, newItem: ExpandedCandidate): Boolean {
                return oldItem.engineIndex == newItem.engineIndex &&
                    oldItem.word.text == newItem.word.text &&
                    oldItem.word.comment == newItem.word.comment
            }

            override fun areContentsTheSame(oldItem: ExpandedCandidate, newItem: ExpandedCandidate): Boolean {
                return oldItem.engineIndex == newItem.engineIndex && oldItem.word == newItem.word
            }
        }
    }

    var offset = 0
        private set

    fun refreshWithOffset(offset: Int) {
        this.offset = offset
        refresh()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        return CandidateViewHolder(CandidateItemUi(parent.context, theme))
    }

    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        val item = getItem(position)
        val candidate = item?.word ?: CandidateWord.Empty
        val engineIndex = item?.engineIndex ?: (position + offset)
        holder.update(engineIndex, candidate, position + 1)
    }
}
