package dev.jfronny.zerointerest

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.connect2x.lognity.api.backend.Backend
import dev.jfronny.zerointerest.ui.App
import dev.jfronny.zerointerest.util.KoinLogWrangler
import dev.jfronny.zerointerest.util.LognityWrangler
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Backend.set(LognityWrangler)
    ComposeViewport {
        KoinApplication(configuration = koinConfiguration(declaration = {
            logger(KoinLogWrangler)
            modules(listOf(createAppModule(), createExtraModule()))
        }), content = {
            App()
        })
    }
}
