package dev.jfronny.zerointerest.service

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val log = KotlinLogging.logger {}

/**
 * Android SSO login handler using Custom Tabs.
 * 
 * This uses a custom URL scheme to receive the callback after SSO authentication.
 * Custom Tabs provide a better user experience compared to opening the browser.
 */
class AndroidSsoLoginHandler(private val context: Context) : SsoLoginHandler {
    
    companion object {
        private const val CALLBACK_SCHEME = "zerointerest"
        
        private val lock = ReentrantLock()
        private var continuation: CancellableContinuation<SsoCallbackResult>? = null
        
        fun handleCallbackUrl(url: String): Boolean {
            if (!url.startsWith("$CALLBACK_SCHEME://")) return false
            
            val loginToken = extractLoginToken(url)
            
            val cont = lock.withLock {
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
    
    override fun getCallbackUrl(homeserver: Url, idpId: String?): String {
        return "$CALLBACK_SCHEME://sso"
    }
    
    override suspend fun performSsoLogin(homeserver: Url, idpId: String?, ssoUrl: String): SsoCallbackResult {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                lock.withLock {
                    continuation = cont
                }
                
                try {
                    val actualSsoUrl = ssoUrl.replace(
                        Regex("redirectUrl=[^&]+"),
                        "redirectUrl=${java.net.URLEncoder.encode(getCallbackUrl(homeserver, idpId), "UTF-8")}"
                    )
                    
                    val customTabsIntent = CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build()
                    
                    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    try {
                        customTabsIntent.launchUrl(context, actualSsoUrl.toUri())
                    } catch (e: Exception) {
                        // Fallback to regular browser
                        val browserIntent = Intent(Intent.ACTION_VIEW, actualSsoUrl.toUri())
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(browserIntent)
                    }
                } catch (e: Exception) {
                    log.error(e) { "SSO login failed to launch" }
                    cont.resumeWithException(e)
                }
                
                cont.invokeOnCancellation {
                    lock.withLock {
                        if (continuation === cont) {
                            continuation = null
                        }
                    }
                }
            }
        }
    }
}

actual fun Scope.createSsoLoginHandler(): SsoLoginHandler = AndroidSsoLoginHandler(androidContext().applicationContext)
