package dev.jfronny.zerointerest

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.connect2x.lognity.api.backend.Backend
import dev.jfronny.zerointerest.ui.App
import dev.jfronny.zerointerest.ui.LoadingScreenExtras
import dev.jfronny.zerointerest.util.KoinLogWrangler
import dev.jfronny.zerointerest.util.LognityWrangler
import js.string.JsStrings.toKotlinString
import kotlinx.browser.window
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import web.url.URLSearchParams
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsString

@OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)
fun main() {
    val params = URLSearchParams(window.location.search)
    operator fun URLSearchParams.get(name: String): String? = get(name.toJsString())?.toKotlinString()

    Backend.set(LognityWrangler)
    ComposeViewport {
        KoinApplication(configuration = koinConfiguration(declaration = {
            logger(KoinLogWrangler)
            modules(createAppModule(), createExtraModule(), module {
                single { LoadingScreenExtras(
                    homeserver = params["homeserver"],
                    idpId = params["idpId"],
                    loginToken = params["loginToken"]
                ) }
            })
        }), content = {
            App()
        })
    }
}
