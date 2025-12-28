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
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class NumberKeyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboard(context, theme, Layout) {

        companion object {
            const val Name = "Number"

            val Layout: List<List<KeyDef>> = listOf(
                listOf(
                    NumPadKey(",", 0x002c, 18f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                    NumPadKey("1", 0xffb1, 22f, 0f),
                    NumPadKey("2", 0xffb2, 22f, 0f),
                    NumPadKey("3", 0xffb3, 22f, 0f),
                    BackspaceKey()
                ),
                listOf(
                    NumPadKey(".", 0x002e, 18f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                    NumPadKey("4", 0xffb4, 22f, 0f),
                    NumPadKey("5", 0xffb5, 22f, 0f),
                    NumPadKey("6", 0xffb6, 22f, 0f),
                    ClearKey("清空")
                ),
                listOf(
                    NumPadKey("?", 0x003f, 18f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                    NumPadKey("7", 0xffb7, 22f, 0f),
                    NumPadKey("8", 0xffb8, 22f, 0f),
                    NumPadKey("9", 0xffb9, 22f, 0f),
                    VoiceKey()
                ),
                listOf(
                    ImageLayoutSwitchKey(R.drawable.ic_baseline_at_24, PickerWindow.Key.Symbol.name, percentWidth =  0.15f,KeyDef.Appearance.Variant.Alternative),
                    LayoutSwitchKey("返回", "",   0f,KeyDef.Appearance.Variant.Alternative),
                    NumPadKey("0", 0xffb0, 22f, 0f),
                    NormalSpaceKey(0.2333333f,KeyDef.Appearance.Variant.Alternative),
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

    @SuppressLint("MissingSuperCall")
    override fun onPopupAction(action: PopupAction) {
        // leave empty on purpose to disable popup in NumberKeyboard
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        var transformed = action
        when (action) {
            is KeyAction.SymAction -> {
                transformed = action.copy(
                    sym = action.sym,
                    states = KeyStates(KeyState.Virtual, KeyState.CapsLock)
                )
            }
            else -> {}
        }
        super.onAction(transformed,source)
    }
}