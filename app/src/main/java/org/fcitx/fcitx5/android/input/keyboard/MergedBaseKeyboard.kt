package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.ColumnKeyView.VH
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.T9TextKeyboard.Companion.columnKey
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add

open class MergedBaseKeyboard(
    context: Context,
    theme: Theme,
    keyLayout: List<List<KeyDef>>,
    columnKey: ColumnKey,
    columnNum: Int
) : BaseKeyboard(context, theme, keyLayout) {
    protected val columnKeyView: ColumnKeyView = createKeyView(columnKey) as ColumnKeyView

    var adapter: ColumnAdapter? = null

    private  val regex = Regex("([A-Za-z ]+)")

    init {
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
                matchConstraintPercentHeight= (1f / keyLayout.size) * columnNum
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
        when(action){
            //清空之后，也需要同步清空 候选词菜单
            is KeyAction.ClearAction -> {
                adapter?.updateItems( (columnKey.appearance as KeyDef.Appearance.Column).children)
                columnKeyView.refreshRecyclerView()
            }
            is KeyAction.FcitxKeyAction ->{
                if(action.act.length == 1 && action.act[0]  in '1'..'9'){
                    val fcitx = FcitxDaemon.getFirstConnectionOrNull()
                    fcitx?.lifecycleScope?.launch {
                        fcitx.runOnReady{
                            val candidates = getCandidates(0, 50)
                            val keys =  mutableListOf<KeyDef>()
                            for(item in candidates){
                                val t = regex.findAll(item).toList()
                                if(t.isNotEmpty()){
                                    keys.add(MixedAlphabetKey(t[0].value,t[0].value))
                                }
                            }
                            columnKeyView.post {
                                adapter?.updateItems(keys)
                                columnKeyView.refreshRecyclerView()
                            }
                        }
                    }
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
                    val appearance = KeyDef.Appearance.Text(
                        displayText = keyDef.appearance.displayText,
                        textSize = keyDef.appearance.textSize,
                        textStyle = keyDef.appearance.textStyle,
                        percentWidth = keyDef.appearance.percentWidth,
                        variant = keyDef.appearance.variant,
                        border = Border.Off,
                        margin = keyDef.appearance.margin,
                        viewId = keyDef.appearance.viewId,
                        soundEffect = keyDef.appearance.soundEffect,
                    )
                    TextKeyView(ctx, theme, appearance)
                }
                else -> error("Unsupported Column child")
            }
            keyDef.behaviors.forEach {
                when (it) {
                    is KeyDef.Behavior.Press -> {
                        view.setOnClickListener { _ ->
                            onActionCallback(it.action)
                        }
                    }
                    else -> {}
                }
            }
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            if (itemHeight > 0) {
                holder.itemView.layoutParams = RecyclerView.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    itemHeight
                )
            }
        }

        override fun getItemCount(): Int = items.size
        override fun getItemViewType(position: Int): Int = position

        /** 动态更新子项数据 */
        fun updateItems(newItems: List<KeyDef>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }
}
