package dev.jfronny.zerointerest.service

import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.MessageEvent
import org.w3c.dom.events.Event
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * JS/Web SSO login handler using popup window and postMessage for callback.
 * According to the Matrix spec, the SSO flow will call `window.opener.postMessage("authDone", "*")`
 * when authentication is complete, with the loginToken in the URL.
 */
class JsSsoLoginHandler : SsoLoginHandler {
    
    override fun getCallbackUrl(): String {
        // For web, we use the current origin with a callback path
        return "${window.location.origin}/sso-callback"
    }
    
    override suspend fun performSsoLogin(ssoUrl: String): SsoCallbackResult {
        return suspendCancellableCoroutine { continuation ->
            // Open SSO URL in a popup window
            val popup = window.open(ssoUrl, "sso_popup", "width=600,height=700")
            
            if (popup == null) {
                continuation.resumeWithException(
                    IllegalStateException("Could not open SSO popup window. Please allow popups for this site.")
                )
                return@suspendCancellableCoroutine
            }
            
            var messageHandler: ((Event) -> Unit)? = null
            var pollInterval: Int? = null
            
            // Listen for postMessage from the popup
            messageHandler = { event: Event ->
                val messageEvent = event as? MessageEvent ?: return@messageHandler
                
                // Check if this is the auth done message
                val data = messageEvent.data
                if (data == "authDone" || (data is String && data.contains("loginToken"))) {
                    // Try to extract loginToken from the popup URL
                    try {
                        val popupUrl = popup.location.href
                        val loginToken = extractLoginToken(popupUrl)
                        
                        if (loginToken != null) {
                            cleanup()
                            popup.close()
                            continuation.resume(SsoCallbackResult(loginToken))
                        }
                    } catch (e: Exception) {
                        // Cross-origin access might fail, try alternative methods
                    }
                }
            }
            
            fun cleanup() {
                messageHandler?.let { window.removeEventListener("message", it) }
                pollInterval?.let { window.clearInterval(it) }
            }
            
            window.addEventListener("message", messageHandler!!)
            
            // Also poll the popup URL for loginToken (fallback)
            pollInterval = window.setInterval({
                try {
                    if (popup.closed == true) {
                        cleanup()
                        continuation.resumeWithException(
                            IllegalStateException("SSO popup was closed without completing login")
                        )
                        return@setInterval
                    }
                    
                    // Try to read the popup URL (will fail if cross-origin)
                    val popupUrl = popup.location.href
                    val loginToken = extractLoginToken(popupUrl)
                    
                    if (loginToken != null) {
                        cleanup()
                        popup.close()
                        continuation.resume(SsoCallbackResult(loginToken))
                    }
                } catch (e: Exception) {
                    // Cross-origin access - ignore and wait for postMessage
                }
            }, 500)
            
            continuation.invokeOnCancellation {
                cleanup()
                popup.close()
            }
        }
    }
    
    private fun extractLoginToken(url: String): String? {
        if (!url.contains("loginToken")) return null
        
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return null
        
        val query = url.substring(queryStart + 1)
        return query.split('&')
            .mapNotNull { param ->
                val parts = param.split('=', limit = 2)
                if (parts.size == 2 && parts[0] == "loginToken") parts[1] else null
            }
            .firstOrNull()
    }
}

actual fun createSsoLoginHandler(): SsoLoginHandler = JsSsoLoginHandler()
