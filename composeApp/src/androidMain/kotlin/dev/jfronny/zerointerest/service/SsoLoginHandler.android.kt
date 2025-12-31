package dev.jfronny.zerointerest.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val log = KotlinLogging.logger {}

/**
 * Android SSO login handler using Custom Tabs.
 * 
 * This uses a local HTTP server to receive the callback after SSO authentication.
 * Custom Tabs provide a better user experience compared to opening the browser.
 */
class AndroidSsoLoginHandler(private val context: Context) : SsoLoginHandler {
    
    companion object {
        // Callback path for local server
        private const val CALLBACK_PATH = "/sso-callback"
        
        // Port range to try for local server
        private const val PORT_START = 18749
        private const val PORT_END = 18759
    }
    
    private var currentPort: Int = PORT_START
    
    override fun getCallbackUrl(): String {
        // Use a local server callback URL
        return "http://localhost:$currentPort$CALLBACK_PATH"
    }
    
    override suspend fun performSsoLogin(ssoUrl: String): SsoCallbackResult {
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                var server: ServerSocket? = null
                
                try {
                    // Find an available port
                    server = findAvailableServer()
                    currentPort = server.localPort
                    
                    // Start listening for callback in background
                    val serverRef = server
                    
                    // Open SSO URL in Custom Tabs (or browser) on main thread
                    val actualSsoUrl = ssoUrl.replace(
                        Regex("redirectUrl=[^&]+"),
                        "redirectUrl=${java.net.URLEncoder.encode(getCallbackUrl(), "UTF-8")}"
                    )
                    
                    context.mainExecutor.execute {
                        try {
                            val customTabsIntent = CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .build()
                            
                            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            
                            try {
                                customTabsIntent.launchUrl(context, Uri.parse(actualSsoUrl))
                            } catch (e: Exception) {
                                // Fallback to regular browser
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(actualSsoUrl))
                                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(browserIntent)
                            }
                        } catch (e: Exception) {
                            log.error(e) { "Failed to launch SSO URL" }
                            serverRef.close()
                            cont.resumeWithException(e)
                        }
                    }
                    
                    // Wait for callback
                    val socket = serverRef.accept()
                    
                    socket.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        val line = reader.readLine() ?: throw IllegalStateException("Empty request")
                        
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
                        
                        val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: ${responseHtml.length}\r\n" +
                            "Connection: close\r\n" +
                            "\r\n" +
                            responseHtml
                        
                        s.getOutputStream().write(response.toByteArray())
                        
                        if (loginToken != null) {
                            cont.resume(SsoCallbackResult(loginToken))
                        } else {
                            cont.resumeWithException(
                                IllegalStateException("No login token received in SSO callback")
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.error(e) { "SSO login failed" }
                    cont.resumeWithException(e)
                } finally {
                    try {
                        server?.close()
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
                
                cont.invokeOnCancellation {
                    try {
                        server?.close()
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }
    
    private fun findAvailableServer(): ServerSocket {
        for (port in PORT_START..PORT_END) {
            try {
                return ServerSocket(port)
            } catch (_: Exception) {
                // Port in use, try next
            }
        }
        throw IllegalStateException("Could not find available port for SSO callback server")
    }
}

// Context will be set early in app initialization
private var appContext: Context? = null
private val contextLock = Any()

fun setAndroidContext(context: Context) {
    synchronized(contextLock) {
        appContext = context.applicationContext
    }
}

actual fun createSsoLoginHandler(): SsoLoginHandler {
    val ctx = synchronized(contextLock) { appContext }
        ?: throw IllegalStateException("Android context not set. Call setAndroidContext() first.")
    return AndroidSsoLoginHandler(ctx)
}
