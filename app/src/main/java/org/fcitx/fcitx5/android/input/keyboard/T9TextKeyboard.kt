/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class T9TextKeyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboard(context, theme, Layout) {

    val numLockState = KeyStates(KeyState.NumLock, KeyState.Virtual)

    companion object {
        const val Name = "T9Text"

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                MixedAlphabetKey("，", "，", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                SegmentKey("分词", percentWidth = 0f),
                MixedAlphabetKey("2","ABC", 16f, 0f),
                MixedAlphabetKey("3","DEF", 16f, 0f),
                BackspaceKey()
            ),
            listOf(
                MixedAlphabetKey("。", "。", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                MixedAlphabetKey("4","GHI",  16f, 0f),
                MixedAlphabetKey("5","JKL",  16f, 0f),
                MixedAlphabetKey("6","MNO", 16f, 0f),
                ClearKey("清空")
            ),
            listOf(
                MixedAlphabetKey("？", "？", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                MixedAlphabetKey("7","PQRS", 16f, 0f),
                MixedAlphabetKey("8","TUV",  16f, 0f),
                MixedAlphabetKey("9","WXYZ",  16f, 0f),
                VoiceKey()
            ),
            listOf(
                ImageLayoutSwitchKey(R.drawable.ic_baseline_at_24, PickerWindow.Key.Symbol.name, percentWidth =  0.15f,KeyDef.Appearance.Variant.Alternative),
                ImageLayoutSwitchKey(R.drawable.ic_baseline_input_mode_cn_icon_24, TextKeyboard.Name, percentWidth = 0f,variant= KeyDef.Appearance.Variant.Alternative),
                NormalSpaceKey(0.2333333f,KeyDef.Appearance.Variant.Alternative),
                ImageLayoutSwitchKey(R.drawable.ic_baseline_number123_24, NumberKeyboard.Name, percentWidth =  0f,KeyDef.Appearance.Variant.Alternative),
                ReturnKey()
            )
        )
    }

    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val space: TextKeyView by lazy { findViewById(R.id.button_mini_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    private fun popupSourceOnAction(action: KeyAction, source: KeyActionListener.Source){
        var transformed = action
        when(action){
            is KeyAction.FcitxKeyAction ->{
                if(numberSymMap.containsKey(action.act)){
                    val sym = numberSymMap[action.act]
                    transformed = KeyAction.SymAction(KeySym(sym = sym?: 0xffb0),
                        numLockState
                    )
                }
            }
            else -> {}
        }
        super.onAction(transformed, source)
    }

    private fun keyboardSourceOnAction(action: KeyAction, source: KeyActionListener.Source){
        val transformed = action
        super.onAction(transformed, source)
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        if (source == KeyActionListener.Source.Popup){
            popupSourceOnAction(action,source)
            return
        }
        keyboardSourceOnAction(action,source)
    }
}