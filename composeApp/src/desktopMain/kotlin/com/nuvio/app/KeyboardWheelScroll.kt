package com.nuvio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import com.nuvio.app.features.player.desktop.DesktopHostOs
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.min

// Arrow and page keys scroll like the mouse wheel: they go back in as precise
// wheel events at the pointer (see PreciseWheel.kt), so whatever the wheel would
// scroll there scrolls, on every screen. Only keys nothing focused consumed get
// here, so text fields keep their arrows.

// A tap moves this far, eased out over a few frames.
private const val STEP_DP = 110.0

// Holding a key past the repeat delay scrolls continuously at this speed.
private const val HOLD_DELAY_NANOS = 250_000_000L
private const val HOLD_SPEED_DP_PER_SECOND = 1400.0

// Share of the remaining distance applied per second while easing out.
private const val EASE_PER_SECOND = 14.0

private const val MAX_FRAME_SECONDS = 1.0 / 30

// Page Up/Down move this share of the window height.
private const val PAGE_FRACTION = 0.85

private class AxisScroll {
    var remainingDp = 0.0
    var heldDirection = 0
    var heldSinceNanos = 0L
    var pendingPixels = 0.0
}

/**
 * Window `onKeyEvent` handler that scrolls with ↑/↓ (vertical), ←/→ (horizontal,
 * like Shift+wheel) and Page Up/Page Down. [Run] drives it from inside the window.
 */
internal class KeyboardWheelScroll {
    private val vertical = AxisScroll()
    private val horizontal = AxisScroll()
    private var running by mutableStateOf(false)
    private var window: Window? = null

    fun onKeyEvent(event: KeyEvent): Boolean {
        val window = window ?: return false
        if (!AppKeyboardShortcuts.keyboardScrollEnabled) return false
        if (event.isMetaPressed || event.isCtrlPressed || event.isAltPressed || event.isShiftPressed) return false
        val (axis, direction) = when (event.key) {
            Key.DirectionDown -> vertical to 1
            Key.DirectionUp -> vertical to -1
            Key.DirectionRight -> horizontal to 1
            Key.DirectionLeft -> horizontal to -1
            Key.PageDown -> vertical to 2
            Key.PageUp -> vertical to -2
            else -> return false
        }
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (abs(direction) == 2) {
                    axis.remainingDp += window.height * PAGE_FRACTION * direction.sign()
                } else if (axis.heldDirection != direction) {
                    // First press (not an auto-repeat): one step, then hold.
                    axis.remainingDp += STEP_DP * direction
                    axis.heldDirection = direction
                    axis.heldSinceNanos = System.nanoTime()
                }
                running = true
            }
            KeyEventType.KeyUp -> {
                if (axis.heldDirection == direction) axis.heldDirection = 0
            }
        }
        return true
    }

    @Composable
    fun Run(window: Window) {
        DisposableEffect(window) {
            this@KeyboardWheelScroll.window = window
            onDispose { this@KeyboardWheelScroll.window = null }
        }
        if (!running) return
        LaunchedEffect(Unit) {
            var lastFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                // Capped so a slow first frame doesn't jump the whole step at once.
                val elapsedSeconds = min((frame - lastFrame) / 1e9, MAX_FRAME_SECONDS)
                lastFrame = frame
                // A key released while another window had focus never reports KeyUp.
                if (!window.isFocused) {
                    vertical.heldDirection = 0
                    horizontal.heldDirection = 0
                }
                val target = window.wheelTarget() ?: break
                val verticalMoving = vertical.advance(elapsedSeconds, target, horizontal = false)
                val horizontalMoving = horizontal.advance(elapsedSeconds, target, horizontal = true)
                if (!verticalMoving && !horizontalMoving) break
            }
            vertical.remainingDp = 0.0
            horizontal.remainingDp = 0.0
            running = false
        }
    }
}

private fun Int.sign(): Int = if (this > 0) 1 else -1

private class WheelTarget(val window: Window, val component: Component, val pointer: Point)

// Returns true while the axis still has something to scroll.
private fun AxisScroll.advance(
    elapsedSeconds: Double,
    target: WheelTarget,
    horizontal: Boolean,
): Boolean {
    var moveDp = 0.0
    if (remainingDp != 0.0) {
        val eased = remainingDp * min(1.0, EASE_PER_SECOND * elapsedSeconds)
        moveDp += eased
        remainingDp -= eased
        if (abs(remainingDp) < 0.5) {
            moveDp += remainingDp
            remainingDp = 0.0
        }
    }
    val holding = heldDirection != 0 &&
        System.nanoTime() - heldSinceNanos >= HOLD_DELAY_NANOS
    if (holding) moveDp += heldDirection * HOLD_SPEED_DP_PER_SECOND * elapsedSeconds
    if (moveDp != 0.0) {
        if (DesktopHostOs.current == DesktopHostOs.MACOS) {
            pendingPixels += moveDp
            pendingPixels = postMacWheel(target, pendingPixels, horizontal)
        } else {
            pendingPixels += moveDp * target.component.displayScale
            pendingPixels = postPreciseWheel(target.component, target.pointer, pendingPixels, horizontal, 0)
        }
    }
    return remainingDp != 0.0 || heldDirection != 0
}

// The Compose component under the pointer, or under the window's centre when
// the pointer is outside the window.
private fun Window.wheelTarget(): WheelTarget? {
    val content = contentPaneIfShowing() ?: return null
    val origin = content.locationOnScreen
    val pointer = pointerInContent()
        ?: Point(origin.x + content.width / 2, origin.y + content.height / 2)
    return WheelTarget(this, componentAt(content, pointer), pointer)
}

private fun Window.contentPaneIfShowing(): java.awt.Container? =
    if (isShowing) (this as? javax.swing.RootPaneContainer)?.contentPane else null

// The pointer in screen coordinates, when it is over the window's content.
internal fun Window.pointerInContent(): Point? {
    val content = contentPaneIfShowing() ?: return null
    val origin = content.locationOnScreen
    return MouseInfo.getPointerInfo()?.location
        ?.takeIf { it.x - origin.x in 0 until content.width && it.y - origin.y in 0 until content.height }
}

internal fun componentAt(content: java.awt.Container, pointer: Point): Component {
    val local = Point(pointer).also { SwingUtilities.convertPointFromScreen(it, content) }
    return SwingUtilities.getDeepestComponentAt(content, local.x, local.y) ?: content
}

// macOS delivers wheel (and mouse) events to the window, which hands them down to Compose;
// one posted to the canvas never reaches it. Compose there scrolls 10 dp per
// unit of precise rotation (MacOSCocoaConfig). Returns the dp left to scroll.
private fun postMacWheel(target: WheelTarget, dp: Double, horizontal: Boolean): Double {
    if (abs(dp) < 0.5) return dp
    val window = target.window
    val local = Point(target.pointer).also { SwingUtilities.convertPointFromScreen(it, window) }
    val event = MouseWheelEvent(
        window,
        MouseEvent.MOUSE_WHEEL,
        System.currentTimeMillis(),
        // Compose reads a shifted wheel as horizontal.
        if (horizontal) InputEvent.SHIFT_DOWN_MASK else 0,
        local.x,
        local.y,
        target.pointer.x,
        target.pointer.y,
        0,
        false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        1,
        0,
        dp / MAC_DP_PER_ROTATION,
    )
    Toolkit.getDefaultToolkit().systemEventQueue.postEvent(event)
    return 0.0
}

private const val MAC_DP_PER_ROTATION = 10.0
