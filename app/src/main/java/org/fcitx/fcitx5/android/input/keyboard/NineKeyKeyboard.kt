/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.annotation.Keep
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.views.imageResource

/**
 * 3×3 数字九键（2–9 带字母）。中文智能拼音、英文多击。
 */
@SuppressLint("ViewConstructor")
class NineKeyKeyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "NineKey"

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                T9Key('1', "，。"),
                T9Key('2', "ABC"),
                T9Key('3', "DEF"),
            ),
            listOf(
                T9Key('4', "GHI"),
                T9Key('5', "JKL"),
                T9Key('6', "MNO"),
            ),
            listOf(
                T9Key('7', "PQRS"),
                T9Key('8', "TUV"),
                T9Key('9', "WXYZ"),
            ),
            listOf(
                LayoutSwitchKey(
                    "26键",
                    TextKeyboard.Name,
                    0.18f,
                    longPressTo = "",
                    altHint = "?123",
                ),
                LanguageKey(),
                SpaceKey(),
                BackspaceKey(0.16f),
                ReturnKey(0.16f),
            ),
        )
    }

    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    private val showLangSwitchKey = AppPrefs.getInstance().keyboard.showLangSwitchKey

    @Keep
    private val showLangSwitchKeyListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        updateLangSwitchKey(v)
    }

    init {
        updateLangSwitchKey(showLangSwitchKey.getValue())
        showLangSwitchKey.registerOnChangeListener(showLangSwitchKeyListener)
    }

    override fun onAttach() {
        T9Session.clear()
    }

    override fun onDetach() {
        T9Session.clear()
        super.onDetach()
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        space.mainText.text = buildString {
            append(ime.displayName)
            ime.subMode.run { label.ifEmpty { name.ifEmpty { null } } }?.let { append(" ($it)") }
        }
    }

    private fun updateLangSwitchKey(visible: Boolean) {
        lang.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
