package com.convx.windows

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.convx.windows.ui.ConvxApp

fun main() = application {
    val state = rememberWindowState(size = DpSize(1180.dp, 780.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "Convx",
    ) {
        ConvxApp()
    }
}
