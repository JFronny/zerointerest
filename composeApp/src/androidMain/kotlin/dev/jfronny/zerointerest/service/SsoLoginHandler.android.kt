package dev.jfronny.zerointerest.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android SSO login handler using Custom Tabs.
 * 
 * This uses a custom scheme URL for the callback, which requires:
 * 1. An Activity with an intent filter for the custom scheme
 * 2. The Activity to broadcast the result back
 * 
 * For simplicity, this implementation uses a local server approach similar to JVM,
 * but custom tabs provide a better user experience on Android.
 */
class AndroidSsoLoginHandler(private val context: Context) : SsoLoginHandler {
    
    companion object {
        // Callback port for local server
        private const val CALLBACK_PORT = 18749
        private const val CALLBACK_PATH = "/sso-callback"
        
        // Store pending callback result
        @Volatile
        private var pendingResult: SsoCallbackResult? = null
        
        @Volatile
        private var pendingError: Throwable? = null
        
        @Volatile
        private var continuation: kotlinx.coroutines.CancellableContinuation<SsoCallbackResult>? = null
        
        /**
         * Called when SSO callback is received.
         */
        fun onSsoCallback(loginToken: String?) {
            if (loginToken != null) {
                continuation?.resume(SsoCallbackResult(loginToken))
            } else {
                continuation?.resumeWithException(
                    IllegalStateException("No login token received in SSO callback")
                )
            }
            continuation = null
        }
        
        /**
         * Called when SSO flow is cancelled.
         */
        fun onSsoCancelled() {
            continuation?.resumeWithException(
                IllegalStateException("SSO login was cancelled")
            )
            continuation = null
        }
    }
    
    override fun getCallbackUrl(): String {
        // Use a local server callback URL
        return "http://localhost:$CALLBACK_PORT$CALLBACK_PATH"
    }
    
    override suspend fun performSsoLogin(ssoUrl: String): SsoCallbackResult {
        return suspendCancellableCoroutine { cont ->
            continuation = cont
            
            try {
                // Start local server for callback (same as JVM)
                startLocalServer()
                
                // Open SSO URL in Custom Tabs (or browser)
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                try {
                    customTabsIntent.launchUrl(context, Uri.parse(ssoUrl))
                } catch (e: Exception) {
                    // Fallback to regular browser
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ssoUrl))
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(browserIntent)
                }
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
            
            cont.invokeOnCancellation {
                stopLocalServer()
                continuation = null
            }
        }
    }
    
    private var server: java.net.ServerSocket? = null
    private var serverThread: Thread? = null
    
    private fun startLocalServer() {
        serverThread = Thread {
            try {
                server = java.net.ServerSocket(CALLBACK_PORT)
                val socket = server?.accept()
                
                socket?.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    val line = reader.readLine() ?: return@use
                    
                    // Parse GET request for loginToken
                    val loginToken = if (line.startsWith("GET")) {
                        val path = line.split(" ").getOrNull(1) ?: ""
                        val query = path.substringAfter("?", "")
                        query.split("&")
                            .mapNotNull { param ->
                                val parts = param.split("=", limit = 2)
                                if (parts.size == 2 && parts[0] == "loginToken") parts[1] else null
                            }
                            .firstOrNull()
                    } else null
                    
                    // Send response
                    val responseHtml = if (loginToken != null) {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><title>Login Successful</title></head>
                        <body>
                            <h1>Login Successful!</h1>
                            <p>You can close this window and return to the app.</p>
                        </body>
                        </html>
                        """.trimIndent()
                    } else {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><title>Login Failed</title></head>
                        <body>
                            <h1>Login Failed</h1>
                            <p>No login token received.</p>
                        </body>
                        </html>
                        """.trimIndent()
                    }
                    
                    val response = """
                        HTTP/1.1 200 OK
                        Content-Type: text/html; charset=UTF-8
                        Content-Length: ${responseHtml.length}
                        Connection: close

                        $responseHtml
                    """.trimIndent()
                    
                    s.getOutputStream().write(response.toByteArray())
                    
                    // Notify result
                    onSsoCallback(loginToken)
                }
            } catch (e: Exception) {
                if (continuation != null) {
                    onSsoCancelled()
                }
            }
        }
        serverThread?.start()
    }
    
    private fun stopLocalServer() {
        try {
            server?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverThread?.interrupt()
        server = null
        serverThread = null
    }
}

// Context will be injected by Koin
private lateinit var appContext: Context

fun setAndroidContext(context: Context) {
    appContext = context
}

actual fun createSsoLoginHandler(): SsoLoginHandler = AndroidSsoLoginHandler(appContext)
