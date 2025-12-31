package dev.jfronny.zerointerest.service

import org.koin.core.scope.Scope

/**
 * Result of an SSO login callback.
 * Contains the login token returned by the Matrix server.
 */
data class SsoCallbackResult(
    val loginToken: String
)

/**
 * Platform-specific SSO login handler.
 * This interface defines how each platform handles the SSO OAuth flow.
 */
interface SsoLoginHandler {
    /**
     * Get the callback URL that the SSO flow should redirect to.
     * This URL is platform-specific:
     * - JVM: A local HTTP server URL
     * - Web: The current page URL with a callback path
     * - Mobile: A custom scheme URL or local server
     */
    fun getCallbackUrl(): String
    
    /**
     * Perform SSO login flow.
     * Opens the SSO URL and waits for the callback with the login token.
     * 
     * @param ssoUrl The URL to open for SSO authentication
     * @return The login token received from the SSO callback
     * @throws Exception if the SSO flow fails or is cancelled
     */
    suspend fun performSsoLogin(ssoUrl: String): SsoCallbackResult
}

/**
 * Factory function to create platform-specific SSO login handler.
 */
expect fun Scope.createSsoLoginHandler(): SsoLoginHandler
