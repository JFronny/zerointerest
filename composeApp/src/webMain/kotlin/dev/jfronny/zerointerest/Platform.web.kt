package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import web.events.EventHandler
import web.window.window

@Composable
actual fun getPlatformTheme(darkTheme: Boolean): ColorScheme? = null

actual fun addShutdownHook(block: () -> Unit) {
    window.onbeforeunload = EventHandler(block)
}
