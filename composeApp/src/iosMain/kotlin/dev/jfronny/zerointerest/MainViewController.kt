package dev.jfronny.zerointerest

import androidx.compose.ui.window.ComposeUIViewController
import dev.jfronny.zerointerest.ui.App
import dev.jfronny.zerointerest.service.Settings
import org.koin.compose.KoinApplication
import org.koin.core.context.loadKoinModules
import org.koin.dsl.bind
import org.koin.dsl.module

fun MainViewController() = ComposeUIViewController {
    KoinApplication(application = {
        loadKoinModules(listOf(createAppModule(), createExtraModule(), createIOSModule()))
    }) {
        App()
    }
}

fun createIOSModule() = module {
    single {
        AppleSettings()
    } bind Settings::class
}
