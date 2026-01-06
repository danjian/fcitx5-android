package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.*
import android.util.TypedValue
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.ColumnKeyView.VH
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.keyboard.T9TextKeyboard.Companion.columnKey
import org.fcitx.fcitx5.android.utils.T9PinYin
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import timber.log.Timber

@SuppressLint("ViewConstructor")
open class MergedBaseKeyboard(
    context: Context,
    theme: Theme,
    keyLayout: List<List<KeyDef>>,
    columnKey: ColumnKey,
    columnNum: Int
) : BaseKeyboard(context, theme, keyLayout) {
    protected val columnKeyView: ColumnKeyView = createKeyView(columnKey) as ColumnKeyView

    var adapter: ColumnAdapter? = null

    val fcitx = FcitxDaemon.connect(javaClass.name)

    var combinations = ArrayDeque<String>()

    val inputs = ArrayDeque<Char>()

    fun isAlphaNumeric(c: Char): Boolean {
        return c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z'
    }

    private val stateScope =
        CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    init {
        fcitx.lifecycleScope.launch {
            fcitx.runImmediately { eventFlow }.collect {
                handleFcitxEvent(it)
            }
        }
        post {
            var t = columnKey.appearance as KeyDef.Appearance.Column
            adapter = ColumnAdapter(context, theme, t.children) { action ->
                this@MergedBaseKeyboard.onAction(action)
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

            val rowCount = keyLayout.size
            if (rowCount == 0) return@post

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
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        super.onAction(action, source)
        when (action) {
            is KeyAction.SymAction -> {
                if (action.states.virtual) {
                    when (action.sym.sym) {
                        FcitxKeyMapping.FcitxKey_BackSpace -> {
                            if (inputs.isNotEmpty()) {
                                inputs.removeLast()
                                refreshColumnView()
                            }
                        }
                    }
                }
            }
            //清空之后，也需要同步清空 候选词菜单
            is KeyAction.ClearAction -> {
                reset()
            }
            is KeyAction.PreeditKeyAction -> {
                selectCombination(action.act)
            }
            is KeyAction.FcitxKeyAction -> {
                if (action.act.length == 1 && isAlphaNumeric(action.act[0])) {
                    inputs.addLast(action.act[0])
                    refreshColumnView()
                }
            }
            else -> {}
        }
    }

    /** RecyclerView Adapter 保留原来的 ColumnAdapter */
    class ColumnAdapter(
        private val ctx: Context,
        private val theme: Theme,
        items: List<KeyDef>,
        private val onActionCallback: (KeyAction) -> Unit  // <- 关键
    ) : RecyclerView.Adapter<VH>() {

        private val items = mutableListOf<KeyDef>().apply { addAll(items) }

        var itemHeight: Int = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val keyDef = items[viewType]
            val view = when (keyDef.appearance) {
                is KeyDef.Appearance.Text -> {
                    keyDef.appearance.apply {
                        border = Border.Off
                    }
                    TextKeyView(ctx, theme, keyDef.appearance)
                }
                else -> error("Unsupported Column child")
            }
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            if (itemHeight > 0) {
                holder.itemView.layoutParams = RecyclerView.LayoutParams(
                    LayoutParams.MATCH_PARENT, itemHeight
                )
            }
            val keyDef = items[position]
            val view = holder.itemView
            when {
                view is TextKeyView && keyDef.appearance is KeyDef.Appearance.Text -> {
                    val appearance = keyDef.appearance
                    view.mainText.apply {
                        background = null
                        text = appearance.displayText
                        setTextSize(TypedValue.COMPLEX_UNIT_DIP, appearance.textSize)
                        textDirection = TEXT_DIRECTION_FIRST_STRONG_LTR
                        // keep original typeface, apply textStyle only
                        setTypeface(typeface, appearance.textStyle)
                        setTextColor(
                            when (appearance.variant) {
                                Variant.Normal -> theme.keyTextColor
                                Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                                Variant.Accent -> theme.accentKeyTextColor
                            }
                        )
                    }
                }
                else -> error("Unsupported Column")
            }
            holder.itemView.setOnClickListener(null)
            keyDef.behaviors.forEach { behavior ->
                if (behavior is KeyDef.Behavior.Press) {
                    holder.itemView.setOnClickListener {
                        onActionCallback(behavior.action)
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size
        override fun getItemViewType(position: Int): Int = position

        /** 动态更新子项数据 */
        @SuppressLint("NotifyDataSetChanged")
        fun updateItems(newItems: List<KeyDef>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }

    protected fun selectCombination(combination: String) {
        val consume = combination.length
        // 1. 防御：不能超过剩余输入
        if (consume > inputs.size) return
        // 2. 递归式消费输入
        repeat(consume) {
            inputs.removeFirst()
        }
        // 3. 记录已确认组合
        combinations.addLast(combination)
        // 4. 构造当前 rime preedit
        val confirmed = combinations.joinToString("'")
        val remaining = inputs.joinToString("")
        fcitx.lifecycleScope.launch {
            fcitx.runOnReady {
                val newInput =  when {
                    confirmed.isEmpty() -> remaining
                    remaining.isEmpty() -> confirmed
                    else -> "$confirmed'$remaining"
                }
                rimeReplaceInput(newInput)
            }
        }
        refreshColumnView()
    }



    private fun refreshColumnView() {
        val inputStr = inputs.joinToString("")
        stateScope.launch {
            val keys = T9PinYin.possibleCombinations(inputStr)
            val items = keys.map { key -> PreeditKey(key, key, 21f - key.length, 0.15f, Variant.Normal) }
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

    override fun reset() {
        combinations.clear()
        inputs.clear()
        refreshColumnView()
    }

    protected fun handleFcitxEvent(event: FcitxEvent<*>) {
        when (event) {
            is FcitxEvent.CommitStringEvent -> {
                reset()
            }
            else -> {}
        }
    }
}
