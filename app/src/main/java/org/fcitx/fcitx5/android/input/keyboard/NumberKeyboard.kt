/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.imageResource
import timber.log.Timber

@SuppressLint("ViewConstructor")
class NumberKeyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboard(context, theme, Layout) {

    var adapter: ColumnViewAdapter? = null

    val columnKeyView: ColumnKeyView = createKeyView(columnKey) as ColumnKeyView

    init {
        var t = columnKey.appearance as KeyDef.Appearance.Column
        adapter = ColumnViewAdapter(context, theme, t.children) { action ->
            this.onAction(action)
        }
        columnKeyView.adapter = adapter
        columnKeyView.id = generateViewId()

        // 添加 ColumnKeyView
        add(columnKeyView, lParams {
            id = columnKeyView.id
            topOfParent()
            leftOfParent()
            matchConstraintPercentWidth = columnKey.appearance.percentWidth
            matchConstraintPercentHeight = (1f / keyLayout.size) * columnNum
        })
        var rowCount = keyLayout.size
        if (rowCount == 0) {
            rowCount = 1
        }

        val rowHeight = height / rowCount
        val visibleRows = 3
        columnKeyView.updateLayoutParams<LayoutParams> {
            height = rowHeight * visibleRows
        }
        keyRows.forEachIndexed { index, row ->
            row.updateLayoutParams<LayoutParams> {
                // ❗关键：先解除 BaseKeyboard 里绑死的约束
                startToStart = LayoutParams.UNSET
                leftToLeft = LayoutParams.UNSET
                rightToRight = LayoutParams.UNSET
                if (index < columnNum) {
                    startToEnd = columnKeyView.id
                } else {
                    startToStart = LayoutParams.PARENT_ID
                }
                endToEnd = LayoutParams.PARENT_ID
            }
        }
    }


    companion object {
        const val Name = "Number"

        var columnNum = 3

        val columnKey = ColumnKey(
            children = listOf(
                CommitKey(",", ",", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey(".", ".", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("?", "?", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey(";", ";", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("!", "!", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("，", "，", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("。", "。", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("？", "？", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("；", "；", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("！", "！", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
            ), percentWidth = 0.15f, KeyDef.Appearance.Variant.Alternative
        )

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                NumPadKey("1", 0xffb1, 22f, 0f),
                NumPadKey("2", 0xffb2, 22f, 0f),
                NumPadKey("3", 0xffb3, 22f, 0f),
                BackspaceKey(percentWidth = 0.175f)
            ), listOf(
                NumPadKey("4", 0xffb4, 22f, 0f),
                NumPadKey("5", 0xffb5, 22f, 0f),
                NumPadKey("6", 0xffb6, 22f, 0f),
                ClearKey("清空", percentWidth = 0.175f)
            ), listOf(
                NumPadKey("7", 0xffb7, 22f, 0f),
                NumPadKey("8", 0xffb8, 22f, 0f),
                NumPadKey("9", 0xffb9, 22f, 0f),
                VoiceKey(percentWidth = 0.175f)
            ), listOf(
                ImageLayoutSwitchKey(
                    R.drawable.ic_baseline_at_24,
                    PickerWindow.Key.Symbol.name,
                    percentWidth = 0.15f,
                    KeyDef.Appearance.Variant.Alternative
                ),
                LayoutSwitchKey("返回", "", 0f, KeyDef.Appearance.Variant.Alternative),
                NumPadKey("0", 0xffb0, 22f, 0f),
                NormalSpaceKey(0.2333333f, KeyDef.Appearance.Variant.Alternative),
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
                    sym = action.sym, states = KeyStates(KeyState.Virtual, KeyState.CapsLock)
                )
            }
            else -> {}
        }
        super.onAction(transformed, source)
    }
}