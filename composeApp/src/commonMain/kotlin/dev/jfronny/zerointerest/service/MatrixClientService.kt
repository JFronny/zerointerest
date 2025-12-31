package dev.jfronny.zerointerest.service

import dev.jfronny.zerointerest.Platform
import dev.jfronny.zerointerest.SuspendLazy
import dev.jfronny.zerointerest.createAppMatrixModule
import dev.jfronny.zerointerest.ui.appName
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.loginWith
import net.folivo.trixnity.client.loginWithPassword
import net.folivo.trixnity.client.loginWithToken
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientFactory
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.LoginType

class MatrixClientService(
    private val platform: Platform,
    private val ssoLoginHandler: SsoLoginHandler
) {
    private val flow: MutableStateFlow<MatrixClient?> = MutableStateFlow(null)
    val client: StateFlow<MatrixClient?> = flow

    fun get(): MatrixClient {
        return flow.value ?: throw IllegalStateException("MatrixClient not initialized")
    }

    private val repositoriesModule = SuspendLazy { platform.getRepositoriesModule() }
    private val mediaStoreModule = SuspendLazy { platform.getMediaStoreModule() }

    /**
     * Get available login types for a homeserver.
     */
    suspend fun getLoginTypes(homeserver: Url): List<LoginType> {
        val apiClient = MatrixClientServerApiClientFactory.default.create(
            baseUrl = homeserver,
            httpClientEngine = platform.getHttpClientEngine()
        )
        return apiClient.use { api ->
            api.authentication.getLoginTypes().getOrThrow().toList()
        }
    }

    suspend fun restore() {
        if (flow.value != null) return
        flow.value = MatrixClient.fromStore(
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            configuration = configuration,
        ).getOrThrow()?.apply {
            startSync()
        }
    }

    suspend fun loginWithPassword(homeserver: Url, username: String, password: String) {
        close()
        flow.value = MatrixClient.loginWithPassword(
            baseUrl = homeserver,
            identifier = IdentifierType.User(username),
            password = password,
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            configuration = configuration,
            initialDeviceDisplayName = "ZeroInterest ${platform.name}",
        ).getOrThrow().apply {
            startSync()
        }
    }

    suspend fun loginWithToken(homeserver: Url, token: String) {
        close()
        flow.value = MatrixClient.loginWithToken(
            baseUrl = homeserver,
            token = token,
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            configuration = configuration,
            initialDeviceDisplayName = "ZeroInterest ${platform.name}",
        ).getOrThrow().apply {
            startSync()
        }
    }

    /**
     * Perform SSO login flow.
     * Opens a browser/webview for the user to authenticate, then completes login with the token.
     */
    suspend fun loginWithSso(homeserver: Url, idpId: String? = null) {
        close()
        
        // Use loginWith to handle the SSO flow
        flow.value = MatrixClient.loginWith(
            baseUrl = homeserver,
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            configuration = configuration,
            getLoginInfo = { api ->
                // Get SSO URL with redirect
                val ssoUrl = api.authentication.getSsoUrl(
                    redirectUrl = ssoLoginHandler.getCallbackUrl(),
                    idpId = idpId
                )
                
                // Perform SSO login flow and get the login token
                val callbackResult = ssoLoginHandler.performSsoLogin(ssoUrl)
                
                // Use the token to login
                api.authentication.login(
                    token = callbackResult.loginToken,
                    type = LoginType.Token(),
                    initialDeviceDisplayName = "ZeroInterest ${platform.name}"
                ).map { login ->
                    MatrixClient.LoginInfo(
                        userId = login.userId,
                        deviceId = login.deviceId,
                        accessToken = login.accessToken,
                        refreshToken = login.refreshToken
                    )
                }
            }
        ).getOrThrow().apply {
            startSync()
        }
    }

    suspend fun logout() {
        flow.value?.logout()
        close()
    }

    private suspend fun close() {
        flow.value?.let {
            it.stopSync()
            it.closeSuspending()
        }
        flow.value = null
    }

    private val configuration: MatrixClientConfiguration.() -> Unit = {
        name = appName
        httpClientEngine = platform.getHttpClientEngine()
        modulesFactories += ::createAppMatrixModule
    }

    val loggedIn get() = flow.value != null
}
