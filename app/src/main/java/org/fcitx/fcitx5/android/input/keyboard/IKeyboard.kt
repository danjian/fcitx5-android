package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.input.popup.PopupActionListener

import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.input.popup.PopupAction

interface IKeyboard  {
    /** 键盘事件监听器 */
    var keyActionListener: KeyActionListener?

    /** 弹出窗口监听器 */
    var popupActionListener: PopupActionListener?

    /** 键盘显示或绑定到界面时调用 */
    fun onAttach()

    /** 键盘从界面移除时调用 */
    fun onDetach()

    /** 收到输入法更新时调用 */
    fun onInputMethodUpdate(ime: InputMethodEntry)

    /** 收到标点更新时调用 */
    fun onPunctuationUpdate(mapping: Map<String, String>)

    /** 返回键图标更新 */
    fun onReturnDrawableUpdate(@DrawableRes returnDrawable: Int)

    /** 主动触发键事件 */
    fun onAction(
        action: KeyAction,
        source: KeyActionListener.Source = KeyActionListener.Source.Keyboard
    )

    /** 主动触发弹出窗口事件 */
    fun onPopupAction(action: PopupAction)

    /** 可选：获取键盘行（仅 BaseKeyboard 和类似布局才有） */
    val keyRows: List<androidx.constraintlayout.widget.ConstraintLayout>?
}
