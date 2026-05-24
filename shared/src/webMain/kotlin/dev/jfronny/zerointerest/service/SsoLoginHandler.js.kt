package dev.jfronny.zerointerest.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.scope.Scope
import kotlin.js.ExperimentalWasmJsInterop

private val log = KotlinLogging.logger {}

/**
 * JS/Web SSO login handler using popup window and postMessage for callback.
 * According to the Matrix spec, the SSO flow will call `window.opener.postMessage("authDone", "*")`
 * when authentication is complete, with the loginToken in the URL.
 */
class JsSsoLoginHandler(private val settings: Settings) : SsoLoginHandler {
    override fun getCallbackUrl(homeserver: Url, idpId: String?): String {
        // For web, we use the current origin with a callback path
        return URLBuilder(window.location.origin).apply {
            parameters.apply {
                append("homeserver", homeserver.toString())
                if (idpId != null) append("idpId", idpId)
            }
        }.buildString()
    }
    
    @OptIn(ExperimentalWasmJsInterop::class, ExperimentalWasmJsInterop::class)
    override suspend fun performSsoLogin(homeserver: Url, idpId: String?, ssoUrl: String): SsoCallbackResult {
        return suspendCancellableCoroutine { continuation ->
            // Open SSO URL in a popup window
            window.open(ssoUrl, "_self", "width=600,height=700")
            // do not continue if popup failed to open
            // this is obviously a hack, but it should work in practice
            log.error { "Failed to open SSO popup window" }
        }
    }
}

actual fun Scope.createSsoLoginHandler(): SsoLoginHandler = JsSsoLoginHandler(get())
