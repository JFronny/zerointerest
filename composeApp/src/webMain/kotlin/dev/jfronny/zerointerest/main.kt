package dev.jfronny.zerointerest

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.connect2x.lognity.api.backend.Backend
import dev.jfronny.zerointerest.ui.App
import dev.jfronny.zerointerest.util.KoinLogWrangler
import dev.jfronny.zerointerest.util.LognityWrangler
import org.koin.compose.KoinApplication
import org.koin.core.module.Module

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Backend.set(LognityWrangler)
    ComposeViewport {
        KoinApplication(application = {
            logger(KoinLogWrangler)
            modules(listOf(createAppModule(), createExtraModule()))
        }) {
            App()
        }
    }
}

expect fun createExtraModule(): Module
