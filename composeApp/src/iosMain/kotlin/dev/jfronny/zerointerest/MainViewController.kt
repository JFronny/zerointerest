package dev.jfronny.zerointerest

import androidx.compose.ui.window.ComposeUIViewController
import dev.jfronny.zerointerest.ui.App
import org.koin.compose.KoinApplication
import org.koin.core.context.loadKoinModules

fun MainViewController() = ComposeUIViewController {
    KoinApplication(application = {
        loadKoinModules(listOf(createAppModule(), createExtraModule()))
    }) {
        App()
    }
}