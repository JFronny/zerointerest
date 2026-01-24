package dev.jfronny.zerointerest

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.connect2x.lognity.api.backend.Backend
import dev.jfronny.zerointerest.ui.App
import dev.jfronny.zerointerest.util.KoinLogWrangler
import dev.jfronny.zerointerest.util.LognityWrangler
import org.koin.compose.KoinApplication

fun main() {
    Backend.set(LognityWrangler)
    application {
        KoinApplication(application = {
            logger(KoinLogWrangler)
            modules(listOf(createAppModule(), createExtraModule()))
        }) {
            Window(
                onCloseRequest = ::exitApplication,
                title = "zerointerest",
            ) {
                App()
            }
        }
    }
}
