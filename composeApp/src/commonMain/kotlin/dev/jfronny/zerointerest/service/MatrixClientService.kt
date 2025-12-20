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
import net.folivo.trixnity.client.loginWithPassword
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType

class MatrixClientService(private val platform: Platform) {
    private val flow: MutableStateFlow<MatrixClient?> = MutableStateFlow(null)
    val client: StateFlow<MatrixClient?> = flow

    fun get(): MatrixClient {
        return flow.value ?: throw IllegalStateException("MatrixClient not initialized")
    }

    private val repositoriesModule = SuspendLazy { platform.getRepositoriesModule() }
    private val mediaStoreModule = SuspendLazy { platform.getMediaStoreModule() }

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

    suspend fun login(homeserver: Url, username: String, password: String) {
        close()
        flow.value = MatrixClient.loginWithPassword(
            baseUrl = homeserver,
            identifier = IdentifierType.User(username),
            password = password,
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            configuration = configuration,
            initialDeviceDisplayName = "ZeroInterest",
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
