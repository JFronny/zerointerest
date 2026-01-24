package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.scope.Scope

interface Platform {
    val name: String

    suspend fun getRepositoriesModule(): RepositoriesModule
    suspend fun getMediaStoreModule(): MediaStoreModule
    fun getHttpClientEngine(): HttpClientEngine
    fun createDataStore(): DataStore<Preferences>
}

expect fun Scope.getPlatform(): Platform
@Composable
expect fun getPlatformTheme(darkTheme: Boolean): ColorScheme?
expect fun addShutdownHook(block: () -> Unit)