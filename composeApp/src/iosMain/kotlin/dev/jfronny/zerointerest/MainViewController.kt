package dev.jfronny.zerointerest

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.compose.KoinApplication
import org.koin.core.context.loadKoinModules

fun MainViewController() = ComposeUIViewController {
    KoinApplication(application = {
        loadKoinModules(createAppModule())
    }) {
        App()
    }
}