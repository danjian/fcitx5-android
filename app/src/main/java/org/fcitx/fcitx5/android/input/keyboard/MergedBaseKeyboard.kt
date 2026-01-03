package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.ColumnKeyView.VH
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.keyboard.T9TextKeyboard.Companion.columnKey
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.textView

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

    companion object {
        val segments: MutableList<String> = mutableListOf<String>()

        var input :String = ""

        fun resetInput() {
            input = ""
        }

        fun appendInput(v:String){
            input += v
        }
    }

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
                resetInput()
                adapter?.updateItems( (columnKey.appearance as KeyDef.Appearance.Column).children)
            }
            is KeyAction.PreeditKeyAction ->{
                for(char in action.act){

                }
            }
            is KeyAction.FcitxKeyAction ->{
                if(action.act.length == 1 && action.act[0]  in '1'..'9'){
                    appendInput(action.act)
                    val fcitx = FcitxDaemon.getFirstConnectionOrNull()
                    fcitx?.lifecycleScope?.launch {
                        Log.d("sssssssss", "ssssss")
                        fcitx.runOnReady{
                            val candidates = getCandidates(0, 100)
                            val keys =  mutableListOf<KeyDef>()
                            val map = mutableMapOf<String, Boolean>()
                            for(item in candidates){
                                val matches = regex.findAll(item).toList()
                                for(item in matches){
                                    val parts = item.value.trim().split(" ")
                                    for(part in parts){
                                        val cpart = part.trim()
                                        if (map.containsKey(cpart) || cpart.isBlank()){
                                            continue
                                        }
                                        Log.d("TTTT", cpart)
                                        map[cpart] = true
                                        keys.add(PreeditKey(cpart,cpart, percentWidth =  0.15f))
                                        break
                                    }
                                    break
                                }
                            }
                            if (keys.isNotEmpty()){
                                withContext(Dispatchers.Main) {
                                        adapter?.updateItems(keys)
                                        columnKeyView.resetPosition()
                                }
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
                    LayoutParams.MATCH_PARENT,
                    itemHeight
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
        fun updateItems(newItems: List<KeyDef>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }
}
