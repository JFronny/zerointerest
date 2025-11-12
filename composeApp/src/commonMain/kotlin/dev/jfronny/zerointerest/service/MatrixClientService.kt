package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.ui.appName
import io.ktor.http.Url
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.login
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.events.m.Presence

class MatrixClientService(private val platform: Platform) {
    var matrixClient: MatrixClient? = null
        private set

    fun get(): MatrixClient {
        return matrixClient ?: throw IllegalStateException("MatrixClient not initialized")
    }

    fun set(client: MatrixClient) {
        matrixClient = client
    }

    private val repositoriesModule = SuspendLazy { platform.getRepositoriesModule() }
    private val mediaStoreModule = SuspendLazy { platform.getMediaStoreModule() }

    suspend fun restore() {
        if (matrixClient != null) return
        matrixClient = MatrixClient.fromStore(
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            configuration = configuration,
        ).getOrThrow()
        matrixClient?.startSync(presence = Presence.OFFLINE)
    }

    suspend fun login(homeserver: Url, username: String, password: String) {
        matrixClient?.stopSync()
        matrixClient = MatrixClient.login(
            baseUrl = homeserver,
            identifier = IdentifierType.User(username),
            password = password,
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            configuration = configuration,
        ).getOrThrow()
        matrixClient?.startSync(presence = Presence.OFFLINE)
    }

    private val configuration: MatrixClientConfiguration.() -> Unit = {
        name = appName
        httpClientEngine = platform.getHttpClientEngine()
        modulesFactories += ::createAppMatrixModule
    }

    val loggedIn get() = matrixClient != null
}
