package dev.jfronny.zerointerest.service

import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.reentrantLock
import io.ktor.utils.io.locks.synchronized
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.scope.Scope
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS SSO login handler using Safari for authentication.
 * 
 * This implementation opens the SSO URL in the default browser
 * and uses a custom URL scheme callback.
 * 
 * Note: For a production app, ASWebAuthenticationSession would be preferred
 * as it provides a more seamless experience. However, that requires additional
 * Swift bridging code. This implementation uses the simpler browser-based approach.
 */
@OptIn(InternalAPI::class)
class IosSsoLoginHandler : SsoLoginHandler {

    companion object {
        private const val CALLBACK_SCHEME = "zerointerest"

        private val lock = reentrantLock()
        private var continuation: CancellableContinuation<SsoCallbackResult>? = null
        
        /**
         * Called when the app receives a callback URL.
         * Should be called from AppDelegate or SceneDelegate when handling URL.
         * Note: This uses non-suspend synchronization since it's called from platform code.
         */
        fun handleCallbackUrl(url: String): Boolean {
            if (!url.startsWith("$CALLBACK_SCHEME://")) return false
            
            val loginToken = extractLoginToken(url)
            
            // Use synchronized block for thread safety
            val cont = synchronized(lock) {
                val c = continuation
                continuation = null
                c
            }
            
            if (loginToken != null && cont != null) {
                cont.resume(SsoCallbackResult(loginToken))
                return true
            } else if (cont != null) {
                cont.resumeWithException(
                    IllegalStateException("No login token received in SSO callback")
                )
                return true
            }
            
            return false
        }
        
        private fun extractLoginToken(url: String): String? {
            val queryStart = url.indexOf('?')
            if (queryStart < 0) return null
            
            val query = url.substring(queryStart + 1)
            return query.split('&').firstNotNullOfOrNull { param ->
                val parts = param.split('=', limit = 2)
                if (parts.size == 2 && parts[0] == "loginToken") parts[1] else null
            }
        }
    }
    
    override fun getCallbackUrl(): String {
        return "$CALLBACK_SCHEME://sso-callback"
    }
    
    override suspend fun performSsoLogin(ssoUrl: String): SsoCallbackResult {
        return suspendCancellableCoroutine { cont ->
            synchronized(lock) {
                continuation = cont
            }
            
            // Open SSO URL in Safari
            val url = NSURL.URLWithString(ssoUrl)
            if (url != null) {
                UIApplication.sharedApplication.openURL(url)
            } else {
                cont.resumeWithException(
                    IllegalStateException("Invalid SSO URL: $ssoUrl")
                )
            }
            
            cont.invokeOnCancellation {
                synchronized(lock) {
                    if (continuation === cont) {
                        continuation = null
                    }
                }
            }
        }
    }
}

actual fun Scope.createSsoLoginHandler(): SsoLoginHandler = IosSsoLoginHandler()
