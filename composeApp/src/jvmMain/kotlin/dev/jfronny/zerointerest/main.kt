package dev.jfronny.zerointerest

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.ui.App
import org.koin.compose.KoinApplication
import org.koin.dsl.bind
import org.koin.dsl.module

fun main() = application {
    KoinApplication(application = {
        modules(listOf(createAppModule(), createExtraModule(), createJVMModule()))
    }) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "zerointerest",
        ) {
            App()
        }
    }
}

fun createJVMModule() = module {
    single {
        JVMSettings(get())
    } bind Settings::class
}
