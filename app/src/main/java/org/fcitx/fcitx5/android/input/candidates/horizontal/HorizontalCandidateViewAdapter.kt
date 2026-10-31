/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.horizontal

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayoutManager
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.CandidateItemUi
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

open class HorizontalCandidateViewAdapter(val theme: Theme) :
    RecyclerView.Adapter<CandidateViewHolder>() {

    init {
        setHasStableIds(true)
    }

    var candidates: Array<CandidateWord> = arrayOf()
        private set

    private var displayOrder: IntArray = intArrayOf()
    private var selectionOrder: IntArray = intArrayOf()
    private var displayNumbers: IntArray = intArrayOf()

    /**
     * How the candidate bar should lay out words:
     * - [CandidateDisplayMode.Macrohard] (default, 巨硬): centered/outward BlackBerry-style
     *   ordering, paired with the bottom-row physical-key shortcuts.
     * - [CandidateDisplayMode.Linear] (普通): plain left-to-right [1, 2, 3, 4, 5] ordering,
     *   no bottom-row shortcuts (the four candidate2-5 preferences are expected to be empty).
     *
     * Mutating this does NOT automatically re-render; callers (typically the hosting component)
     * trigger a refresh by calling [updateCandidates] with the same data after assignment.
     */
    var displayMode: CandidateDisplayMode = CandidateDisplayMode.Macrohard

    /**
     * 是否高亮「首选」（切片下标 0）。拼音空格上首选时为 true；
     * 英文单词联想时空格上屏已打字母，点候选才换词，故为 false，避免假首选。
     */
    var highlightPreferred: Boolean = true

    var total = -1
        private set

    /**
     * @param engineIndices 与 [data] 等长时，选词下标用这组引擎原始下标（拼音里丢掉英文词后不能用数组下标）。
     *   为空则仍是 [selectionBase] + 切片内下标。
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateCandidates(
        data: Array<CandidateWord>,
        total: Int,
        selectionBase: Int = 0,
        engineIndices: IntArray? = null,
    ) {
        this.candidates = data
        this.displayOrder = buildDisplayOrder(data.size)
        val indexed = engineIndices?.takeIf { it.size == data.size }
        this.selectionOrder = IntArray(displayOrder.size) { slot ->
            val sliceIndex = displayOrder[slot]
            if (indexed != null) indexed[sliceIndex] else selectionBase + sliceIndex
        }
        this.displayNumbers = displayOrder.map { it + 1 }.toIntArray()
        this.total = total
        notifyDataSetChanged()
    }

    override fun getItemCount() = displayOrder.size

    fun selectionIndexAtDisplayPosition(position: Int): Int? = selectionOrder.getOrNull(position)

    fun selectionIndexForDisplayNumber(number: Int): Int? {
        val idx = displayNumbers.indexOfFirst { it == number }
        if (idx < 0) return null
        return selectionOrder.getOrNull(idx)
    }

    override fun getItemId(position: Int): Long {
        val originalIndex = displayOrder.getOrNull(position) ?: return RecyclerView.NO_ID
        return candidates.getOrNull(originalIndex).hashCode().toLong()
    }

    @CallSuper
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        val ui = CandidateItemUi(parent.context, theme)
        ui.root.apply {
            minimumWidth = dp(40)
            setPaddingDp(10, 0, 10, 0)
            layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, matchParent)
        }
        return CandidateViewHolder(ui)
    }

    @CallSuper
    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        val originalIndex = displayOrder[position]
        val isActive = highlightPreferred && originalIndex == 0
        // Extra padding only when Space commits this preferred word (Chinese).
        val horizontalPaddingDp = if (isActive) 20 else 10
        holder.ui.root.setPaddingDp(horizontalPaddingDp, 0, horizontalPaddingDp, 0)
        val prefs = AppPrefs.getInstance().candidateBar
        val showIndex = prefs.showCandidateIndex.getValue()
        val indexFontSize = prefs.candidateIndexFontSize.getValue()
        val textFontSize = prefs.candidateTextFontSize.getValue()
        holder.update(selectionOrder[position], candidates[originalIndex], displayNumbers[position], isActive, showIndex, indexFontSize, textFontSize)
    }

    @CallSuper
    override fun onViewRecycled(holder: CandidateViewHolder) {
        holder.clear()
    }

    private fun buildDisplayOrder(size: Int): IntArray = when (displayMode) {
        CandidateDisplayMode.Linear -> IntArray(size) { it }
        CandidateDisplayMode.Macrohard -> buildMacrohardDisplayOrder(size)
    }

    private fun buildMacrohardDisplayOrder(size: Int): IntArray {
        if (size <= 0) return intArrayOf()
        if (size == 1) return intArrayOf(0)
        if (size == 2) return intArrayOf(0, 1)
        if (size == 3) return intArrayOf(1, 0, 2)
        if (size == 4) return intArrayOf(1, 0, 2, 3)
        if (size == 5) return intArrayOf(3, 1, 0, 2, 4)

        val order = ArrayList<Int>(size)
        val maxLeftEven = if (size % 2 == 0) size - 2 else size - 1
        for (value in maxLeftEven downTo 2 step 2) {
            order += value - 1
        }
        order += 0

        val maxRightOdd = if (size % 2 == 0) size - 1 else size
        for (value in 3..maxRightOdd step 2) {
            order += value - 1
        }
        if (size % 2 == 0) {
            order += size - 1
        }
        return order.toIntArray()
    }

}
