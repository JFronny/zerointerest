package dev.jfronny.zerointerest

import androidx.compose.ui.window.ComposeUIViewController
import de.connect2x.lognity.api.backend.Backend
import dev.jfronny.zerointerest.ui.App
import dev.jfronny.zerointerest.util.KoinLogWrangler
import dev.jfronny.zerointerest.util.LognityWrangler
import org.koin.compose.KoinApplication
import org.koin.core.context.loadKoinModules
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    Backend.set(LognityWrangler)
    return ComposeUIViewController {
        KoinApplication(application = {
            logger(KoinLogWrangler)
            loadKoinModules(listOf(createAppModule(), createExtraModule()))
        }) {
            App()
        }
    }
}
