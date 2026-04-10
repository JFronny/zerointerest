package dev.jfronny.zerointerest

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.connect2x.lognity.api.backend.Backend
import dev.jfronny.zerointerest.ui.App
import dev.jfronny.zerointerest.util.KoinLogWrangler
import dev.jfronny.zerointerest.util.LognityWrangler
import js.string.JsStrings.toKotlinString
import kotlinx.browser.window
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import web.url.URLSearchParams
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.toJsString

@OptIn(ExperimentalWasmJsInterop::class)
private external interface Opener : JsAny {
    fun postMessage(message: String, targetOrigin: String)
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)
fun main() {
    val loginToken = URLSearchParams(window.location.search)
        .get("loginToken".toJsString())
        ?.toKotlinString()

    if (loginToken != null) {
        println("Received login token: $loginToken")
        (window.opener as Opener?)?.postMessage("authDone?loginToken=$loginToken", "*")
        return
    }

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
