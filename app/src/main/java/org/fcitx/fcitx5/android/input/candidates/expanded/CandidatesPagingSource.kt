/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.candidates.expanded

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.input.candidates.LatinCandidate
import timber.log.Timber

class CandidatesPagingSource(val fcitx: FcitxConnection, val total: Int, val offset: Int) :
    PagingSource<Int, ExpandedCandidate>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExpandedCandidate> {
        // use candidate index for key, null means load from beginning (with offset)
        val startIndex = params.key ?: offset
        val pageSize = params.loadSize
        Timber.d("getCandidates(offset=$startIndex, limit=$pageSize)")
        val (ime, candidates) = fcitx.runOnReady {
            inputMethodEntryCached.uniqueName to getCandidates(startIndex, pageSize)
        }
        val dropped = LatinCandidate.drop(
            candidates,
            LatinCandidate.hidesInIme(ime),
            startIndex,
        )
        val page = List(dropped.words.size) { i ->
            ExpandedCandidate(dropped.engineIndices[i], dropped.words[i])
        }
        val prevKey = if (startIndex >= pageSize) startIndex - pageSize else null
        val nextKey = if (total > 0) {
            if (startIndex + pageSize + 1 >= total) null else startIndex + pageSize
        } else {
            if (candidates.size < pageSize) null else startIndex + pageSize
        }
        return LoadResult.Page(page, prevKey, nextKey)
    }

    // always reload from beginning
    override fun getRefreshKey(state: PagingState<Int, ExpandedCandidate>) = null

}
