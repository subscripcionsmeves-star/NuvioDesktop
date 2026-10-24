package com.nuvio.app

import com.nuvio.app.features.player.desktop.DesktopHostOs
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * Installs the app-wide navigation shortcuts on [window]:
 *  - Primary+1..4: Home, Search, Library, Settings (pressing again on the current tab scrolls it to the top)
 *  - Primary+K / Primary+F: Search with the field focused
 *  - Primary+,: Settings
 *  - Primary+D: Downloads
 *  - Cmd+[ (macOS) / Alt+← (Windows, Linux): back (Esc keeps working as before)
 *  - Primary+/ or F1: shortcut help
 * Primary is ⌘ on macOS and Ctrl elsewhere.
 */
internal fun installDesktopKeyboardShortcuts(window: Window): () -> Unit {
    val isMac = DesktopHostOs.current == DesktopHostOs.MACOS
    AppKeyboardShortcuts.primaryModifierLabel = if (isMac) "⌘" else "Ctrl"
    AppKeyboardShortcuts.backShortcutLabel = if (isMac) "⌘+[" else "Alt+←"

    val dispatcher = KeyEventDispatcher { event ->
        if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
        if (!event.belongsTo(window)) return@KeyEventDispatcher false
        val shortcut = event.toAppKeyboardShortcut(isMac) ?: return@KeyEventDispatcher false
        AppKeyboardShortcuts.dispatch(shortcut)
        true
    }
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
    return {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
    }
}

private fun KeyEvent.belongsTo(window: Window): Boolean {
    val source = component ?: return false
    return source === window || SwingUtilities.getWindowAncestor(source) === window
}

private fun KeyEvent.toAppKeyboardShortcut(isMac: Boolean): AppKeyboardShortcut? {
    val modifiers = modifiersEx
    val meta = modifiers and KeyEvent.META_DOWN_MASK != 0
    val ctrl = modifiers and KeyEvent.CTRL_DOWN_MASK != 0
    val alt = modifiers and KeyEvent.ALT_DOWN_MASK != 0
    val shift = modifiers and KeyEvent.SHIFT_DOWN_MASK != 0

    if (keyCode == KeyEvent.VK_F1 && !meta && !ctrl && !alt) return AppKeyboardShortcut.ShowHelp

    // Back: the platform's usual history combo.
    if (isMac && meta && !ctrl && !alt && keyCode == KeyEvent.VK_OPEN_BRACKET) return AppKeyboardShortcut.Back
    if (!isMac && alt && !ctrl && !meta && keyCode == KeyEvent.VK_LEFT) return AppKeyboardShortcut.Back

    // Everything else needs the primary modifier alone (Cmd+Ctrl+F stays the macOS fullscreen combo).
    val primaryOnly = if (isMac) meta && !ctrl && !alt else ctrl && !meta && !alt
    if (!primaryOnly) return null

    if (keyCode == KeyEvent.VK_SLASH) return AppKeyboardShortcut.ShowHelp
    if (shift) return null
    return when (keyCode) {
        KeyEvent.VK_1, KeyEvent.VK_NUMPAD1 -> AppKeyboardShortcut.Home
        KeyEvent.VK_2, KeyEvent.VK_NUMPAD2 -> AppKeyboardShortcut.Search
        KeyEvent.VK_3, KeyEvent.VK_NUMPAD3 -> AppKeyboardShortcut.Library
        KeyEvent.VK_4, KeyEvent.VK_NUMPAD4 -> AppKeyboardShortcut.Settings
        KeyEvent.VK_K, KeyEvent.VK_F -> AppKeyboardShortcut.Search
        KeyEvent.VK_COMMA -> AppKeyboardShortcut.Settings
        KeyEvent.VK_D -> AppKeyboardShortcut.Downloads
        else -> null
    }
}
