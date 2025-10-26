package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.ui.appName
import io.ktor.http.Url
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.login
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType

class MatrixClientService(private val platform: Platform) {
    private var matrixClient: MatrixClient? = null

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
    }

    suspend fun login(homeserver: Url, username: String, password: String) {
        matrixClient = MatrixClient.login(
            baseUrl = homeserver,
            identifier = IdentifierType.User(username),
            password = password,
            repositoriesModule = repositoriesModule.get(),
            mediaStoreModule = mediaStoreModule.get(),
            configuration = configuration,
        ).getOrThrow()
    }

    private val configuration: MatrixClientConfiguration.() -> Unit = {
        name = appName
        httpClientEngine = platform.getHttpClientEngine()
    }

    val loggedIn get() = matrixClient != null
}
