package dev.jfronny.zerointerest

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.connect2x.lognity.api.backend.Backend
import dev.jfronny.zerointerest.ui.App
import dev.jfronny.zerointerest.util.KoinLogWrangler
import dev.jfronny.zerointerest.util.LognityWrangler
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

fun main() {
    Backend.set(LognityWrangler)
    application {
        KoinApplication(configuration = koinConfiguration(declaration = {
            logger(KoinLogWrangler)
            modules(listOf(createAppModule(), createExtraModule()))
        }), content = {
            Window(
                onCloseRequest = ::exitApplication,
                title = "zerointerest",
            ) {
                App()
            }
        })
    }
}
