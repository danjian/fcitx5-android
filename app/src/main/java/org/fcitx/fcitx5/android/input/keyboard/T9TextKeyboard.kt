/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.utils.T9PinYin
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.imageResource
import timber.log.Timber

@SuppressLint("ViewConstructor")
open class T9TextKeyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboard(context, theme, Layout) {

    protected val columnKeyView: ColumnKeyView = createKeyView(columnKey) as ColumnKeyView

    val segmentChar = '\''

    var adapter: ColumnViewAdapter? = null

    val fcitx = FcitxDaemon.connect(javaClass.name)

    //所有的输入内容
    val inputs = ArrayDeque<Char>()

    //上次已确认的内容长度
    var lastConfirmPos = 0

    //已选的拼音组合
    var pinyinCombinations = ArrayDeque<String>()

    private var fcitxEventJob: Job? = null

    val numLockState = KeyStates(KeyState.NumLock, KeyState.Virtual)

    companion object {
        const val Name = "T9Text"

        var columnNum = 3

        val columnKey = ColumnKey(
            children = listOf(
                CommitKey("，", "，", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("。", "。", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("？", "？", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("；", "；", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("！", "！", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey(",", ",", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey(".", ".", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("?", "?", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey(";", ";", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
                CommitKey("!", "!", 20f, 0.15f, KeyDef.Appearance.Variant.Alternative),
            ), percentWidth = 0.15f, KeyDef.Appearance.Variant.Alternative
        )

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                SegmentKey("分词", percentWidth = 0f),
                MixedAlphabetKey("2", "ABC", 16f, 0f),
                MixedAlphabetKey("3", "DEF", 16f, 0f),
                BackspaceKey(percentWidth = 0.175f)
            ), listOf(
                MixedAlphabetKey("4", "GHI", 16f, 0f),
                MixedAlphabetKey("5", "JKL", 16f, 0f),
                MixedAlphabetKey("6", "MNO", 16f, 0f),
                ClearKey("清空", percentWidth = 0.175f)
            ), listOf(
                MixedAlphabetKey("7", "PQRS", 16f, 0f),
                MixedAlphabetKey("8", "TUV", 16f, 0f),
                MixedAlphabetKey("9", "WXYZ", 16f, 0f),
                VoiceKey(percentWidth = 0.175f)
            ), listOf(
                ImageLayoutSwitchKey(
                    R.drawable.ic_baseline_at_24,
                    PickerWindow.Key.Symbol.name,
                    percentWidth = 0.15f,
                    Variant.Alternative
                ), ImageLayoutSwitchKey(
                    R.drawable.ic_baseline_input_mode_cn_icon_24,
                    TextKeyboard.Name,
                    percentWidth = 0f,
                    variant = Variant.Alternative
                ), NormalSpaceKey(0.2333333f, Variant.Alternative), ImageLayoutSwitchKey(
                    R.drawable.ic_baseline_number123_24,
                    NumberKeyboard.Name,
                    percentWidth = 0f,
                    Variant.Alternative
                ), ReturnKey()
            )
        )

        var numberSymMap = mapOf(
            "1" to 0xffb1,
            "2" to 0xffb2,
            "3" to 0xffb3,
            "4" to 0xffb4,
            "5" to 0xffb5,
            "6" to 0xffb6,
            "7" to 0xffb7,
            "8" to 0xffb8,
            "9" to 0xffb9,
            "0" to 0xffb0,
        )
    }


    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val space: TextKeyView by lazy { findViewById(R.id.button_mini_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    init {
        var t = columnKey.appearance as KeyDef.Appearance.Column
        adapter = ColumnViewAdapter(context, theme, t.children) { action ->
            this@T9TextKeyboard.onAction(action)
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
        if (rowCount == 0){
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

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    protected fun selectCombination(combination: String) {
        pinyinCombinations.add(combination)
        val cs = pinyinCombinations.joinToString(segmentChar.toString())
        val inputStr = inputs.joinToString("")
        val confirmed = inputStr.take(lastConfirmPos)             // 已确认的部分
        val inserted = cs + segmentChar.toString()                    // 新插入组合 + 分隔符
        val remaining = inputStr.drop(lastConfirmPos + cs.length).trimStart(segmentChar) // 剩余未确认部分
        fcitx.lifecycleScope.launch {
            fcitx.runOnReady {
                Timber.tag("KKK").d(confirmed + inserted + remaining)
                rimeReplaceInput(confirmed + inserted + remaining)
            }
        }
    }

    private fun refreshColumnView() {
        var cLen = pinyinCombinations.joinToString(segmentChar.toString()).length
        if (cLen > 0) {
            cLen += 1
        }
        val leftInputStr = inputs.joinToString("").drop(lastConfirmPos + cLen)
        fcitx.lifecycleScope.launch {
            val keys = T9PinYin.possibleCombinations(leftInputStr)
            val items = mutableListOf<PreeditKey>()
            for (key in keys) {
                if (key.isNotBlank()) {
                    items.add(PreeditKey(key, key, 21f - key.length, 0.15f, Variant.Normal))
                }
            }
            columnKeyView.post {
                if (items.isNotEmpty()) {
                    adapter?.updateItems(items)
                    columnKeyView.resetPosition()
                } else {
                    resetColumnView()
                }
            }
        }
    }


    private fun resetColumnView() {
        adapter?.updateItems((columnKey.appearance as KeyDef.Appearance.Column).children)
        columnKeyView.resetPosition()
    }

    protected suspend fun handleFcitxEvent(event: FcitxEvent<*>) {
        when (event) {
            is FcitxEvent.InputPanelEvent -> {
                fcitx.runOnReady {
                    Timber.tag("AAA").d(rimeGetCompositionText())
                    rebuild(rimeGetInput(), rimeGetConfirmedPosition())
                }
            }
            else -> {}
        }
    }

    private fun rebuild(rawInput: String, confirmedPos: Int) {
        Timber.tag("BBB").d(rawInput)
        Timber.tag("CCC").d(confirmedPos.toString())
        if (rawInput.isBlank() && inputs.joinToString("").isBlank()) {
            return
        }
        //用户已经选择了内容
        inputs.clear()
        if (rawInput.isNotBlank()) {
            inputs.addAll(rawInput.toList())
        }
        //如果已确认的位置发生变化，那么需要重新算
        if (lastConfirmPos != confirmedPos) {
            pinyinCombinations.clear()
        }
        lastConfirmPos = confirmedPos
        if (lastConfirmPos > rawInput.length) {
            lastConfirmPos = rawInput.length
        }
        refreshColumnView()
    }

    override fun reset() {
        stopFcitxEvent()
        pinyinCombinations.clear()
        inputs.clear()
        lastConfirmPos = 0
        resetColumnView()
        fcitx.lifecycleScope.launch {
            fcitx.runOnReady {
                reset()
            }
        }
    }

    override fun onAttach() {
        super.onAttach()
        startFcitxEvent()
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        Timber.tag("ZZZ").d("onAction T9TextKeyboard")
        when (action) {
            is KeyAction.PreeditKeyAction -> {
                return selectCombination(action.act)
            }
            else -> {}
        }
        if (source == KeyActionListener.Source.Keyboard) {
            return super.onAction(action, source)
        }
        return popupSourceOnAction(action, source)
    }

    private fun popupSourceOnAction(action: KeyAction, source: KeyActionListener.Source) {
        var transformed = action
        when (action) {
            is KeyAction.FcitxKeyAction -> {
                if (numberSymMap.containsKey(action.act)) {
                    val sym = numberSymMap[action.act]
                    transformed = KeyAction.SymAction(
                        KeySym(sym = sym ?: 0xffb0), numLockState
                    )
                }
            }
            else -> {}
        }
        super.onAction(transformed, source)
    }

    fun startFcitxEvent() {
        if (fcitxEventJob?.isActive == true) {
            return
        }
        fcitxEventJob = fcitx.lifecycleScope.launch {
            fcitx.runImmediately { eventFlow }.collect {
                handleFcitxEvent(it)
            }
        }
    }

    fun stopFcitxEvent() {
        fcitxEventJob?.cancel()
        fcitxEventJob = null
    }
}