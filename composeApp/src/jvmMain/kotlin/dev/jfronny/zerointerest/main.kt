package dev.jfronny.zerointerest

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.jfronny.zerointerest.ui.App
import org.koin.compose.KoinApplication

fun main() = application {
    KoinApplication(application = {
        modules(createAppModule())
    }) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "zerointerest",
        ) {
            App()
        }
    }
}
