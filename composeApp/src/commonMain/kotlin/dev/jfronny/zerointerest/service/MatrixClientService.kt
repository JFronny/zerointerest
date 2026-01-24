package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.create
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.clientserverapi.client.ClassicMatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClientFactory
import de.connect2x.trixnity.clientserverapi.client.classicLoginWith
import de.connect2x.trixnity.clientserverapi.client.classicLoginWithPassword
import de.connect2x.trixnity.clientserverapi.client.classicLoginWithToken
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.clientserverapi.model.authentication.LoginType
import dev.jfronny.zerointerest.Platform
import dev.jfronny.zerointerest.SuspendLazy
import dev.jfronny.zerointerest.createAppMatrixModule
import dev.jfronny.zerointerest.ui.appName
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
        val apiClient = (object : MatrixClientServerApiClientFactory {}).create(
            baseUrl = homeserver,
            httpClientEngine = platform.getHttpClientEngine()
        )
        return apiClient.use { api ->
            api.authentication.getLoginTypes().getOrThrow().toList()
        }
    }

    suspend fun restore() {
        if (flow.value != null) return
        flow.value = MatrixClient.create(
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            configuration = configuration,
        ).getOrThrow().apply {
            startSync()
        }
    }

    suspend fun loginWithPassword(homeserver: Url, username: String, password: String) {
        close()
        flow.value = MatrixClient.create(
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            configuration = configuration,
            authProviderData = MatrixClientAuthProviderData.classicLoginWithPassword(
                baseUrl = homeserver,
                httpClientEngine = platform.getHttpClientEngine(),
                initialDeviceDisplayName = "ZeroInterest ${platform.name}",
                identifier = IdentifierType.User(username),
                password = password,
            ).getOrThrow(),
        ).getOrThrow().apply {
            startSync()
        }
    }

    suspend fun loginWithToken(homeserver: Url, token: String) {
        close()
        flow.value = MatrixClient.create(
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            configuration = configuration,
            authProviderData = MatrixClientAuthProviderData.classicLoginWithToken(
                baseUrl = homeserver,
                httpClientEngine = platform.getHttpClientEngine(),
                initialDeviceDisplayName = "ZeroInterest ${platform.name}",
                token = token,
            ).getOrThrow(),
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
        flow.value = MatrixClient.create(
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            configuration = configuration,
            authProviderData = MatrixClientAuthProviderData.classicLoginWith(
                baseUrl = homeserver,
                httpClientEngine = platform.getHttpClientEngine(),
            ) { api ->
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
                ).getOrThrow().let { login ->
                    ClassicMatrixClientAuthProviderData(
                        baseUrl = homeserver,
                        accessToken = login.accessToken,
                        accessTokenExpiresInMs = login.accessTokenExpiresInMs,
                        refreshToken = login.refreshToken,
                    )
                }
            }.getOrThrow(),
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
