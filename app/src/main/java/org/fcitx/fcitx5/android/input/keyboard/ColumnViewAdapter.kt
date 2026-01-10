package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.View.TEXT_DIRECTION_FIRST_STRONG_LTR
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.ColumnKeyView.VH
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant


/** RecyclerView Adapter 保留原来的 ColumnAdapter */
class ColumnViewAdapter(
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