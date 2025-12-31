package dev.jfronny.zerointerest.service

import com.sun.net.httpserver.HttpServer
import dev.jfronnz.zerointerest.InternalHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.scope.Scope
import java.net.InetSocketAddress
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * JVM SSO login handler using a local HTTP server to receive the callback.
 */
class JvmSsoLoginHandler : SsoLoginHandler {
    companion object {
        private const val CALLBACK_PATH = "/sso-callback"
        // Port range to try for local server
        private const val PORT_START = 18749
        private const val PORT_END = 18759
        private val uriHandler = InternalHelper.getUriHandler()
    }
    
    private var currentPort: Int = PORT_START
    
    override fun getCallbackUrl(): String {
        return "http://localhost:$currentPort$CALLBACK_PATH"
    }
    
    override suspend fun performSsoLogin(ssoUrl: String): SsoCallbackResult {
        return suspendCancellableCoroutine { continuation ->
            // Find an available port
            val server = findAvailableServer()
            currentPort = server.address.port
            
            server.createContext(CALLBACK_PATH) { exchange ->
                val query = exchange.requestURI.query ?: ""
                val params = query.split("&")
                    .mapNotNull { 
                        val parts = it.split("=", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else null
                    }
                    .toMap()
                
                val loginToken = params["loginToken"]
                
                val responseHtml = if (loginToken != null) {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Login Successful</title></head>
                    <body>
                        <h1>Login Successful!</h1>
                        <p>You can close this window and return to the application.</p>
                        <script>window.close();</script>
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
                        <p>No login token received. Please try again.</p>
                    </body>
                    </html>
                    """.trimIndent()
                }
                
                val responseBytes = responseHtml.toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
                exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                exchange.responseBody.use { it.write(responseBytes) }
                
                // Stop the server after processing
                server.stop(0)
                
                if (loginToken != null) {
                    continuation.resume(SsoCallbackResult(loginToken))
                } else {
                    continuation.resumeWithException(
                        IllegalStateException("No login token received in SSO callback")
                    )
                }
            }
            
            server.start()
            
            // Update the SSO URL with the correct redirect URL
            val actualSsoUrl = ssoUrl.replace(
                Regex("redirectUrl=[^&]+"),
                "redirectUrl=${java.net.URLEncoder.encode(getCallbackUrl(), "UTF-8")}"
            )

            uriHandler.openUri(actualSsoUrl)
            
            continuation.invokeOnCancellation {
                server.stop(0)
            }
        }
    }
    
    private fun findAvailableServer(): HttpServer {
        for (port in PORT_START..PORT_END) {
            try {
                return HttpServer.create(InetSocketAddress(port), 0)
            } catch (_: Exception) {
                // Port in use, try next
            }
        }
        throw IllegalStateException("Could not find available port for SSO callback server")
    }
}

actual fun Scope.createSsoLoginHandler(): SsoLoginHandler = JvmSsoLoginHandler()
