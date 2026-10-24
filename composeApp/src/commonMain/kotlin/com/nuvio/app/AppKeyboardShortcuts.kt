package com.nuvio.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal enum class AppKeyboardShortcut {
    Home,
    Search,
    Library,
    Settings,
    Downloads,
    Back,
    ShowHelp,
}

/**
 * Window-level keyboard shortcuts. The platform layer (desktop: an AWT key dispatcher)
 * recognises the key combos and publishes them here, so they work regardless of which
 * Compose node currently holds focus; MainAppContent turns them into navigation.
 */
internal object AppKeyboardShortcuts {
    private val mutableRequests = MutableSharedFlow<AppKeyboardShortcut>(extraBufferCapacity = 8)
    val requests: SharedFlow<AppKeyboardShortcut> = mutableRequests.asSharedFlow()

    /** Label for the primary modifier shown in the help dialog ("⌘" on macOS, "Ctrl" elsewhere). */
    var primaryModifierLabel: String = "Ctrl"

    /** Label for the "back" combo shown in the help dialog. */
    var backShortcutLabel: String = "Alt+←"

    /** Off while the player is shown: it handles arrow keys and Enter itself. */
    var keyboardScrollEnabled: Boolean = true

    fun dispatch(shortcut: AppKeyboardShortcut): Boolean = mutableRequests.tryEmit(shortcut)
}
