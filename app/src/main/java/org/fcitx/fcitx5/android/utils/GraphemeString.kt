package org.fcitx.fcitx5.android.utils

import com.ibm.icu.text.BreakIterator
import java.util.Locale

class GraphemeString(private val raw: String) {

    private data class G(
        val start: Int, val end: Int
    )

    private val gs: List<G> by lazy(LazyThreadSafetyMode.NONE) {
        if (raw.isEmpty()) return@lazy emptyList()

        val it = BreakIterator.getCharacterInstance(Locale.ROOT)
        it.setText(raw)

        val list = ArrayList<G>(raw.length)
        var s = it.first()
        var e = it.next()
        while (e != BreakIterator.DONE) {
            list.add(G(s, e))
            s = e
            e = it.next()
        }
        list
    }

    /** 用户感知长度（唯一长度定义） */
    fun length(): Int = gs.size

    /** 是否为空 */
    fun isEmpty(): Boolean = gs.isEmpty()

    /** grapheme 子串 */
    fun sub(start: Int, end: Int): GraphemeString {
        if (start >= end) return GraphemeString("")
        if (start < 0 || end > gs.size) throw IndexOutOfBoundsException()
        return GraphemeString(
            raw.substring(gs[start].start, gs[end - 1].end)
        )
    }

    /** 反转（grapheme 级） */
    fun reversed(): GraphemeString {
        if (gs.isEmpty()) return this
        val sb = StringBuilder(raw.length)
        for (i in gs.indices.reversed()) {
            val g = gs[i]
            sb.append(raw, g.start, g.end)
        }
        return GraphemeString(sb.toString())
    }

    fun get(i: Int): GraphemeString {
        if (i < 0 || i >= gs.size) {
            throw IndexOutOfBoundsException("index=$i size=${gs.size}")
        }
        val g = gs[i]
        return GraphemeString(raw.substring(g.start, g.end))
    }

    private fun sameAt(i: Int, other: GraphemeString): Boolean {
        val g1 = gs[i]
        val g2 = other.gs[i]
        return raw.substring(g1.start, g1.end) == other.raw.substring(g2.start, g2.end)
    }


    override fun toString(): String = raw
}
