package dev.jfronny.zerointerest

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.jfronny.zerointerest.ui.App
import org.koin.compose.KoinApplication

@OptIn(ExperimentalComposeUiApi::class)
fun main() = ComposeViewport {
    KoinApplication(application = {
        modules(listOf(createAppModule(), createExtraModule()))
    }) {
        App()
    }
}