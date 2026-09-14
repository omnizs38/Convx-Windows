package com.convx.windows

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.convx.windows.ui.ConvxApp
import com.convx.windows.ui.PlayerState
import java.awt.Dimension

/**
 * Desktop entry point.
 *
 * The window keeps the native Windows title bar and system menu - resize, snap, Win+Arrow
 * tiling and the taskbar preview all behave the way the OS expects, and only the client
 * area is ours to draw. Playback state is hoisted up here so the window-level key handler
 * can service media keys while any part of the UI has focus.
 */
fun main() = application {
    val player = remember { PlayerState() }
    val windowState = rememberWindowState(
        size = DpSize(1320.dp, 840.dp),
        position = WindowPosition(Alignment.Center),
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Convx",
        onKeyEvent = { event -> handleGlobalShortcut(event, player) },
    ) {
        // Below this the sidebar, top bar and player bar start overlapping each other.
        LaunchedEffect(window) {
            window.minimumSize = Dimension(1040, 680)
        }
        ConvxApp(player)
    }
}

/**
 * Window-level shortcuts. `onKeyEvent` runs only after the focused component declines the
 * event, so Space still types a space inside the search field instead of toggling playback.
 */
private fun handleGlobalShortcut(event: KeyEvent, player: PlayerState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Spacebar, Key.MediaPlayPause -> {
            player.togglePlay()
            true
        }
        Key.MediaNext -> {
            player.next()
            true
        }
        Key.MediaPrevious -> {
            player.previous()
            true
        }
        Key.Escape -> {
            if (player.isNowPlayingExpanded) {
                player.isNowPlayingExpanded = false
                true
            } else {
                false
            }
        }
        else -> false
    }
}
