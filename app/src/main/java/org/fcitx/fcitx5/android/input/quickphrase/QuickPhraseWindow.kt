/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.quickphrase

import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.quickphrase.QuickPhraseSearch
import org.fcitx.fcitx5.android.data.theme.CandidateFont
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.padding

class QuickPhraseWindow : InputWindow.ExtendedInputWindow<QuickPhraseWindow>() {

    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val windowManager: InputWindowManager by manager.must()
    private val theme by manager.theme()

    override val title: String
        get() = context.getString(R.string.quickphrase_window_title)

    private var query = ""
    private var all = emptyList<QuickPhraseSearch.Hit>()
    private var visible = emptyList<QuickPhraseSearch.Hit>()

    private lateinit var queryView: TextView
    private lateinit var emptyView: TextView
    private lateinit var list: RecyclerView

    private val adapter = object : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val tv = TextView(parent.context).apply {
                textSize = 16f
                setTextColor(theme.keyTextColor)
                typeface = CandidateFont.uiTypeface(context)
                padding = dp(12)
                minHeight = dp(40)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            return Holder(tv)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val hit = visible[position]
            val label = if (position < 5) "${position + 1}  " else "   "
            val extra = hit.keyword.takeIf { it.isNotBlank() }?.let { "  $it" }.orEmpty()
            holder.text.text = "$label${hit.phrase}$extra"
            holder.text.setOnClickListener { commit(hit) }
        }

        override fun getItemCount() = visible.size
    }

    private class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateView(): View = with(context) {
        val keyBorder = ThemeManager.prefs.keyBorder.getValue()
        queryView = textView {
            textSize = 14f
            setTextColor(this@QuickPhraseWindow.theme.altKeyTextColor)
            typeface = CandidateFont.uiTypeface(context)
            padding = dp(12)
        }
        emptyView = textView {
            textSize = 14f
            setTextColor(this@QuickPhraseWindow.theme.altKeyTextColor)
            typeface = CandidateFont.uiTypeface(context)
            padding = dp(12)
            visibility = View.GONE
        }
        list = recyclerView {
            layoutManager = LinearLayoutManager(context)
            adapter = this@QuickPhraseWindow.adapter
        }
        verticalLayout {
            if (!keyBorder) backgroundColor = this@QuickPhraseWindow.theme.barColor
            add(queryView, lParams(matchParent, wrapContent))
            add(emptyView, lParams(matchParent, wrapContent))
            add(list, lParams(matchParent, matchParent))
        }
    }

    override fun onAttached() {
        all = QuickPhraseSearch.load()
        query = ""
        refresh()
    }

    override fun onDetached() {
        query = ""
        all = emptyList()
        visible = emptyList()
    }

    fun onHardwareKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return true
        }
        return consumeKey(event.unicodeChar.toChar().takeIf { it.code != 0 }?.toString(), event.keyCode)
    }

    fun consumeKey(typed: String?, keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (query.isNotEmpty()) query = query.dropLast(1)
                refresh()
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                visible.firstOrNull()?.let { commit(it) }
                return true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                windowManager.attachWindow(KeyboardWindow)
                return true
            }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5,
            -> {
                val idx = keyCode - KeyEvent.KEYCODE_1
                visible.getOrNull(idx)?.let { commit(it) }
                return true
            }
        }
        val text = typed.orEmpty()
        if (text.length == 1 && text[0] in '1'..'5') {
            visible.getOrNull(text[0] - '1')?.let { commit(it) }
            return true
        }
        if (text.isNotEmpty()) {
            val ch = text[0]
            val code = ch.code
            if (ch.isLetterOrDigit() || code in 0x4E00..0x9FFF) {
                query += if (ch.isLetter()) ch.lowercaseChar() else ch
                refresh()
                return true
            }
        }
        return true
    }

    private fun refresh() {
        visible = QuickPhraseSearch.filter(all, query)
        queryView.text = if (query.isEmpty()) {
            context.getString(R.string.quickphrase_window_hint)
        } else {
            context.getString(R.string.quickphrase_window_query, query)
        }
        emptyView.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = context.getString(R.string.quickphrase_window_empty)
        adapter.notifyDataSetChanged()
    }

    private fun commit(hit: QuickPhraseSearch.Hit) {
        service.currentInputConnection?.commitText(hit.phrase, 1)
        windowManager.attachWindow(KeyboardWindow)
    }
}
