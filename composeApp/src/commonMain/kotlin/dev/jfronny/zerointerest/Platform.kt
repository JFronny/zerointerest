package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.core.scope.Scope

interface Platform {
    val name: String

    suspend fun getRepositoriesModule(): Module
    suspend fun getMediaStoreModule(): Module
    fun getHttpClientEngine(): HttpClientEngine
    fun createDataStore(): DataStore<Preferences>
}

expect fun Scope.getPlatform(): Platform
@Composable
expect fun getPlatformTheme(darkTheme: Boolean): ColorScheme?
