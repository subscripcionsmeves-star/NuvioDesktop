package com.nuvio.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.awt.Component
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/**
 * Window `onKeyEvent` handler: Enter clicks whatever is under the mouse pointer,
 * so a poster or button can be opened from the keyboard once the pointer is on it.
 * Only keys nothing focused consumed get here, so focused buttons and text fields
 * keep their own Enter. Space is left alone: typing it would click.
 */
internal class KeyboardPointerClick {
    var window: Window? = null
    private var enterDown = false

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.key != Key.Enter && event.key != Key.NumPadEnter) return false
        if (event.type == KeyEventType.KeyUp) {
            val wasDown = enterDown
            enterDown = false
            return wasDown
        }
        if (event.type != KeyEventType.KeyDown) return false
        // Auto-repeat while held clicks only once.
        if (enterDown) return true
        if (event.isMetaPressed || event.isCtrlPressed || event.isAltPressed || event.isShiftPressed) return false
        if (!AppKeyboardShortcuts.keyboardScrollEnabled) return false
        val window = window ?: return false
        val pointer = window.pointerInContent() ?: return false
        enterDown = true
        clickAt(window, pointer)
        return true
    }
}

// Posts a left click at the pointer (screen coordinates) to the component under
// it, the same one real mouse clicks reach. The move first puts Compose's hover
// state there, as a real pointer would.
internal fun clickAt(window: Window, pointer: Point) {
    val content = (window as? RootPaneContainer)?.contentPane ?: return
    val target: Component = componentAt(content, pointer)
    val local = Point(pointer).also { SwingUtilities.convertPointFromScreen(it, target) }
    val queue = Toolkit.getDefaultToolkit().systemEventQueue
    fun post(id: Int, modifiers: Int) {
        queue.postEvent(
            MouseEvent(
                target, id, System.currentTimeMillis(), modifiers,
                local.x, local.y, pointer.x, pointer.y,
                1, false, MouseEvent.BUTTON1,
            ),
        )
    }
    post(MouseEvent.MOUSE_MOVED, 0)
    post(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK)
    post(MouseEvent.MOUSE_RELEASED, 0)
    post(MouseEvent.MOUSE_CLICKED, 0)
}
