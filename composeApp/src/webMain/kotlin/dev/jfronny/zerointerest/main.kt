package dev.jfronny.zerointerest

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.koin.compose.KoinApplication

@OptIn(ExperimentalComposeUiApi::class)
fun main() = ComposeViewport {
    KoinApplication(application = {
        modules(createAppModule())
    }) {
        App()
    }
}